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
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;

public class Peer implements RMITesting {

	// General constants
	private static final int baseTimeoutMS = 1000;
	private static final int maxAttempts = 5;
	private static final int minResponseWaitMS = 0;
	private static final int maxResponseWaitMS = 400;
	
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
	
	private ProtocolState rcvProtocol = new ProtocolState(new ServiceMessage()); // ASK concurrent hash map?
	private ProtocolState initProtocol = new ProtocolState(new ServiceMessage());
	private static Peer singleton = new Peer();
	
	/**
	 * Private constructor for singleton pattern.
	 */
	private Peer() {}
	
	/**
	 * @return the singleton instance of the class
	 */
	public static Peer getInstance( ) {
		return singleton;
	}
	
	/**
	 * Initialises singleton Peer object. A Peer object handles protocol initiation and also requests for protocol handling from other initiators on known UDP multicast channels.
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
	public void initPeer(String protocolVersion, int peerID, String accessPoint, InetAddress mccAddr, int mccPort, InetAddress mdbAddr, int mdbPort, InetAddress mdrAddr, int mdrPort) {
		
		this.protocolVersion = protocolVersion;
		this.peerID = peerID;
		this.accessPoint = accessPoint;
		
		this.mcc = new ServiceChannel(mccAddr, mccPort, "mcc");
		this.mdb = new ServiceChannel(mdbAddr, mdbPort, "mdb");
		this.mdr = new ServiceChannel(mdrAddr, mdrPort, "mdr");
		
		this.initRMI();
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
	public void receiveLoop() throws IOException, InterruptedException {
		
		while(true) {

			this.rcvProtocol = new ProtocolState(new ServiceMessage());
			
			this.rcvProtocol.setPacket(receivePacket(this.mdb, 0));
			if(this.rcvProtocol.getPacket() == null) continue;
			this.rcvProtocol.setFields(parseHeader(this.rcvProtocol));
			if(this.rcvProtocol.getFields() == null) continue;
			
			this.runProtocol(this.rcvProtocol);
		}
	}
	
	/**
	 * Attempts to receive a packet on a specific channel with specified timeout (ms).
	 * 
	 * @param channel the service channel to listen on
	 * @param timeout the timeout in milliseconds
	 * @return the packet received
	 */
	private DatagramPacket receivePacket(ServiceChannel channel, int timeout) throws IOException {
		
		DatagramPacket packet = channel.listen(timeout);
		return packet;
	}
	
	/**
	 * Validate message as a backup system service message and return its header fields if valid.
	 * 
	 * @param packet the packet to extract the header from
	 * @return the header fields, or null if invalid
	 */
	private String[] parseHeader(ProtocolState state) {
		
		if(!state.getParser().findHeaderIndices(state.getPacket())) return null;
		String[] header = state.getParser().stripHeader(state.getPacket());
		if(header == null) return null;
		
		return header;
	}
	
	/**
	 * Runs a specific protocol based on the protocol field of a service message.
	 * 
	 * @param fields the header fields
	 * @param packet the packet that originated the header fields
	 */
	private void runProtocol(ProtocolState state) throws IOException, InterruptedException {
		
		String protocol = state.getFields()[protocolI].toUpperCase();
		
		// Run handler for each known protocol type
		switch(protocol) {
		
		// BACKUP protocol initiated
		case "PUTCHUNK":

			this.handleBackup(state);
			break;
			
		// BACKUP response
		case "STORED":
			
			this.handleStored(state);
			break;
		}
	}

	/**
	 * Handles a BACKUP protocol by writing the received chunk only if it doesn't exist already and then sending
	 * a response STORED message. Doesn't write the chunk if the receiver and sender are the same.
	 * 
	 * @param fields the header fields
	 * @param packet the packet that originated the header fields
	 */
	private void handleBackup(ProtocolState state) throws IOException, InterruptedException {

		byte[] bodyData = state.getParser().stripBody(state.getPacket());
		
	    // Check if this Peer is the Peer that requested the backup
	    if(Integer.parseInt(state.getFields()[senderI]) == this.peerID) {
	    	SystemManager.getInstance().logPrint("own chunk so won't store", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
		
	    // LATER add to local database
	    
	    // Create file structure for this chunk
		String peerFolder = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix;
	    String chunkFolder = peerFolder + "/" + state.getFields()[hashI];
	    String chunkPath = chunkFolder + "/" + state.getFields()[backChunkNoI];
	    this.createDirIfNotExists(peerFolder);
	    this.createDirIfNotExists(chunkFolder);
	    
	    // Write chunk data to file only if it doesn't already exist
	    File file = new File(chunkPath);
	    if(!file.exists()) {
	    	
	    	FileOutputStream output = new FileOutputStream(file);
	    	output.write(bodyData);
	    	output.close();
	    	
			SystemManager.getInstance().logPrint("written: " + state.getFields()[hashI] + "." + state.getFields()[backChunkNoI], SystemManager.LogLevel.NORMAL);
	    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
	    
	    // Prepare the necessary fields for the response message
	    state.initBackupResponseState(this.protocolVersion, state.getFields()[hashI], state.getFields()[backChunkNoI]);
	    
	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(minResponseWaitMS, maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);
	    Thread.sleep(waitTimeMS); // LATER use timeout on thread handler
	    
	    byte[] msg = state.getParser().createStoredMsg(this.peerID, state);
	    this.mcc.send(msg);
	}
	
	/**
	 * Handles the responses to a BACKUP protocol by counting the unique STORED responses
	 * up to the desired replication degree for this protocol instance.
	 * 
	 * @param fields the header fields
	 */
	private void handleStored(ProtocolState state) {
		
		if(state.getRespondedID().add(Integer.parseInt(state.getFields()[senderI])))
			SystemManager.getInstance().logPrint("added peer ID \"" + state.getFields()[senderI] + "\" to responded", SystemManager.LogLevel.DEBUG);
		
		int responseCount = state.getRespondedID().size();
		int desiredCount = state.getDesiredRepDeg();
		
		String respondedMsg = responseCount + " / " + desiredCount + " unique peers have responded";
		SystemManager.getInstance().logPrint(respondedMsg, SystemManager.LogLevel.DEBUG);

		if(responseCount >= desiredCount) {
			state.incrementCurrentChunkNo();
			state.setStoredCountCorrect(true);
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
		this.initProtocol = new ProtocolState(ProtocolState.ProtocolType.INIT_BACKUP, new ServiceMessage());
		if(!this.initProtocol.initBackupState(this.protocolVersion, filepath, repDeg)) {
			String backFileSize = "cannot backup files larger than 64GB!";
			SystemManager.getInstance().logPrint(backFileSize, SystemManager.LogLevel.NORMAL);
			return;
		}

		// Send PUTCHUNK and wait for repDeg STORED responses
		while(this.initProtocol.getAttempts() < maxAttempts) {
		
			// Prepare and send next PUTCHUNK message
			byte[] msg = this.initProtocol.getParser().createPutchunkMsg(this.peerID, this.initProtocol);
			this.mdb.send(msg);

			// Wait for desired replication degree number of STORED messages up to a timeout value
			if(!this.storedLoop(this.initProtocol)) continue;
			
			if(this.initProtocol.isFinished()) break;
		}
		
		// LATER add to local database
		
		// Finish protocol instance
		if(this.initProtocol.isFinished()) SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
		else {
			this.initProtocol.setFinished(true);
			SystemManager.getInstance().logPrint("failed " + backMsg, SystemManager.LogLevel.NORMAL);
		}
	}
	
	// DOC
	private boolean storedLoop(ProtocolState state) throws IOException, InterruptedException {
		
		while(true) {
			
			// Wait for packets and validate them as a STORED message
			state.setPacket(this.receivePacket(this.mcc, (int) (baseTimeoutMS * Math.pow(2, state.getAttempts()))));

			if(state.getPacket() == null) {
				state.incrementAttempts();
				return false;
			}

			state.setFields(this.parseHeader(state));

			if(state.getFields() == null) {
				state.incrementAttempts();
				return false;
			}
			
			// TODO move to run protocol for verification
			String protocol = state.getFields()[protocolI].toUpperCase();
			if(!protocol.equals("STORED")) {
				state.incrementAttempts();
				return false;
			}

			// Run STORED handler and verify STORED count
			this.runProtocol(state);
			if(state.isStoredCountCorrect()) {
				state.resetStoredCount();
				return true;
			}
		}
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
