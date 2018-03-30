import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;

public class Peer implements RMITesting {

	// Peer storage area fixed strings
	private static final String peerFolderPrefix = "Peer_";
	private static final String peerFolderSuffix = "_Area";
	
	// General header indices
	private static final int protocolI = 0;
	private static final int protocolVersionI = 1;
	private static final int senderI = 2;
	private static final int hashI = 3;
	
	// Backup header indices
	private static final int backChunkNoI = 4;
	private static final int backRepDegI = 5;
	
	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	
	// Sockets for multicast channels
	private ServiceChannel mcc;
	private ServiceChannel mdb;
	private ServiceChannel mdr;
	
	private ProtocolState currProtocol = new ProtocolState(); // ASK concurrent hash map?
	
	/**
	 * A Peer object handles protocol initiation and also requests for protocol handling from other initiators on known UDP multicast channels.
	 * Peers have the function of managing a file backup service and store information about their own database and what they believe the system currently has stored.
	 * 
	 * @param protocolVersion the backup system version
	 * @param peerID the numeric identifier of the Peer
	 * @param accessPoint service access point (RMI Object name)
	 * @param mccAddr address of the multicast control channel
	 * @param mccPort port for the multicast control channel
	 * @param mdbAddr address of the multicast data backup channel
	 * @param mdbPort port for the multicast data backup channel
	 * @param mdrAddr address of the multicast data restore channel
	 * @param mdrPort port for the multicast data restore channel
	 */
	public Peer(String protocolVersion, int peerID, String accessPoint, InetAddress mccAddr, int mccPort, InetAddress mdbAddr, int mdbPort, InetAddress mdrAddr, int mdrPort) {
		
		this.protocolVersion = protocolVersion;
		this.peerID = peerID;
		this.accessPoint = accessPoint;
		
		this.mcc = new ServiceChannel(mccAddr, mccPort, "mcc");
		this.mdb = new ServiceChannel(mdbAddr, mdbPort, "mdb");
		this.mdr = new ServiceChannel(mdrAddr, mdrPort, "mdr");
	}
	
	/**
	 * Register remote object in RMI registry so it can be called by TestApp 
	 */
	public void initRMI() {
		
        try {
        	RMITesting stub = (RMITesting) UnicastRemoteObject.exportObject(this, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(this.accessPoint, stub);

            String msg = "RMI started with remote name \"" + this.accessPoint + "\"";
            SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);
        } catch(Exception e) {
        	
            String msg = "RMI exception: " + e.toString();
            SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);
            e.printStackTrace();
        }
	}
	
	// DOC
	public void receiveLoop() throws IOException {
		
		while(true) {

			// TODO ProtocolState chunkNo, etc
			// TODO STORED message response (random 0 to 400ms delay)
			// TODO timeout on PUTCHUNK
			
			DatagramPacket packet = this.mdb.listen();

			// Validate message as backup system message, then validate and extract the header
			ServiceMessage parser = new ServiceMessage();
			if(!parser.findHeaderIndices(packet)) continue;
			String[] headerFields = parser.stripHeader(packet);
			if(headerFields == null) continue;
			
			this.runProtocol(headerFields, packet);
		}
	}
	
	// DOC
	private void runProtocol(String[] fields, DatagramPacket packet) throws IOException {
		
		String protocol = fields[protocolI].toUpperCase();
		
		// Run handler for each known protocol type
		switch(protocol) {
		
		// BACKUP protocol initiated
		case "PUTCHUNK":

			handleBackup(fields, packet);
			break;
		}
	}
	
	// DOC
	private void handleBackup(String[] fields, DatagramPacket packet) throws IOException {
	
		ServiceMessage parser = new ServiceMessage();
		byte[] bodyData = parser.stripBody(packet);
		
	    // Check if this Peer is the Peer that requested the backup
	    if(Integer.parseInt(fields[senderI]) == this.peerID) {
	    	SystemManager.getInstance().logPrint("own chunk so won't store", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
		
	    // Create file structure for this chunk
		String peerFolder = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix;
	    String chunkFolder = peerFolder + "/" + fields[hashI];
	    String chunkPath = chunkFolder + "/" + fields[backChunkNoI];
	    this.createDirIfNotExists(peerFolder);
	    this.createDirIfNotExists(chunkFolder);
	    
	    // Write chunk data to file only if it doesn't already exist
	    File file = new File(chunkPath);
	    if(!file.exists()) {
	    	
	    	FileOutputStream output = new FileOutputStream(file);
	    	output.write(bodyData);
	    	output.close();
	    	
			SystemManager.getInstance().logPrint("written: " + fields[hashI] + "." + fields[backChunkNoI], SystemManager.LogLevel.NORMAL);
	    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
	    
	    ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.BACKUP);
	    state.initBackupResponseState(this.protocolVersion, fields[hashI], fields[backChunkNoI]);
	    
	    byte[] msg = parser.createStoredMsg(this.peerID, state);
	    this.mcc.send(msg);
	}
	
	/**
	 * Creates directory specified by path if it doesn't already exist.
	 * 
	 * @param dirPath the directory path to create
	 */
	private void createDirIfNotExists(String dirPath) {
		
	    File directory = new File(dirPath);
	    if(!directory.exists()) {
	        directory.mkdir();
	    }
	}

	@Override
	public void remoteBackup(String filepath, int repDeg) throws IOException, NoSuchAlgorithmException, InterruptedException {
		
		String backMsg = "backup: " + filepath + " - " + repDeg;
		SystemManager.getInstance().logPrint("started " + backMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state for backing up the file
		this.currProtocol = new ProtocolState(ProtocolState.ProtocolType.INIT_BACKUP);
		if(!this.currProtocol.initBackupState(this.protocolVersion, filepath, repDeg)) {
			String backFileSize = "cannot backup files larger than 64GB!";
			SystemManager.getInstance().logPrint(backFileSize, SystemManager.LogLevel.NORMAL);
			return;
		}

		while(true) {
		
			// Prepare and send next PUTCHUNK message
			ServiceMessage sMsg = new ServiceMessage();
			byte[] msg = sMsg.createPutchunkMsg(this.peerID, this.currProtocol);
			this.mdb.send(msg);

			// Increment current chunk number and finish if last chunk has been sent
			if(!this.currProtocol.incrementCurrentChunkNo()) break;
			Thread.sleep(500);
		}
		
		// Finish protocol instance
		this.currProtocol.setFinished(true);
		SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
	}
	
	@Override
	public void remoteRestore(String filepath) throws RemoteException {
		
		// TODO restore protocol

		String resMsg = "restore: " + filepath;
		SystemManager.getInstance().logPrint("started " + resMsg, SystemManager.LogLevel.NORMAL);
		SystemManager.getInstance().logPrint("finished " + resMsg, SystemManager.LogLevel.NORMAL);

		return;
	}

	@Override
	public void remoteDelete(String filepath) throws RemoteException {
		
		// TODO delete protocol
		
		String delMsg = "delete: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);

		return;
	}

	@Override
	public void remoteReclaim(int maxKB) throws RemoteException {
		
		// TODO reclaim protocol

		String reclMsg = "reclaim: " + maxKB;
		SystemManager.getInstance().logPrint("started " + reclMsg, SystemManager.LogLevel.NORMAL);
		SystemManager.getInstance().logPrint("finished " + reclMsg, SystemManager.LogLevel.NORMAL);
		
		return;
	}

	@Override
	public String remoteGetInfo() throws RemoteException {
		
		// TODO info protocol

		return null;
	}

}
