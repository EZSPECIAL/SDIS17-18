import java.io.File;
import java.io.FileNotFoundException;
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

	private static final String peerFolderPrefix = "Peer_";
	private static final String peerFolderSuffix = "_Area";
	private static final int senderI = 2;
	private static final int hashI = 3;
	private static final int chunkNoI = 4;
	
	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	
	// Sockets for multicast channels
	private ServiceChannel mcc;
	private ServiceChannel mdb;
	private ServiceChannel mdr;
	
	private ProtocolState currProtocol; // ASK concurrent hash map?
	
	// DOC document
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
	
	// DOC document
	public void receiveLoop() throws IOException {
		
		while(true) {
			
			DatagramPacket packet = this.mdb.listen();

			// Validate message as backup system standard message
			ServiceMessage parser = new ServiceMessage();
			if(!parser.findHeaderIndices(packet)) continue;
			
			// Get header/body and validate header as valid protocol and fields
			String[] headerFields = parser.stripHeader(packet);
			byte[] bodyData = parser.stripBody(packet);
			
			// TODO ProtocolState chunkNo, etc
			// TODO STORED message response
		    
		    // Check if this Peer is the Peer that requested the backup
		    if(Integer.parseInt(headerFields[senderI]) == this.peerID) {
		    	SystemManager.getInstance().logPrint("own chunk so won't store", SystemManager.LogLevel.DEBUG);
		    	continue;
		    }
			
		    // Create file structure for this chunk
			String peerFolder = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix;
		    String chunkFolder = peerFolder + "/" + headerFields[hashI];
		    String chunkPath = chunkFolder + "/" + headerFields[chunkNoI];
		    this.createDirIfNotExists(peerFolder);
		    this.createDirIfNotExists(chunkFolder);
		    
		    // Write chunk data to file only if it doesn't already exist
		    File file = new File(chunkPath);
		    if(!file.exists()) {
		    	
		    	FileOutputStream output = new FileOutputStream(file);
		    	output.write(bodyData);
		    	output.close();
		    	
				SystemManager.getInstance().logPrint("written: " + headerFields[hashI] + "." + headerFields[chunkNoI], SystemManager.LogLevel.NORMAL);
		    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
		}
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
			byte[] msg = sMsg.putChunk(this.peerID, this.currProtocol);
			this.mdb.send(msg);

			// Increment current chunk number and finish if last chunk has been sent
			if(!this.currProtocol.incrementCurrentChunkNo()) break;
			Thread.sleep(500);
		}
		
		SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
	}
	
	@Override
	public void remoteRestore(String filepath) throws RemoteException {
		
		// TODO restore protocol
		// TODO proper log message
		
		System.out.println("restore: " + filepath);
		return;
	}

	@Override
	public void remoteDelete(String filepath) throws RemoteException {
		
		// TODO delete protocol
		// TODO proper log message
		
		System.out.println("delete: " + filepath);
		return;
	}

	@Override
	public void remoteReclaim(int maxKB) throws RemoteException {
		
		// TODO reclaim protocol
		// TODO proper log message
		
		System.out.println("reclaim: " + maxKB);
		return;
	}

	@Override
	public String remoteGetInfo() throws RemoteException {
		
		// TODO info protocol
		// TODO proper log message
		
		System.out.println("info");
		return null;
	}

}
