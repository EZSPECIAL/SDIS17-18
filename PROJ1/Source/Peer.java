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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Peer implements RMITesting {

	// General constants
	private static final int baseTimeoutMS = 1000;
	private static final int maxAttempts = 5;
	private static final int minResponseWaitMS = 0;
	private static final int maxResponseWaitMS = 400;
	private static final int deleteWaitMS = 150;
	private static final int restoreWaitMS = 400;
	private static final int restoreChunkWaitMS = 400;

	private static final String peerFolderPrefix = "Peer_";
	private static final String peerFolderSuffix = "_Area";
	private static final String restoredFolderName = "Restored";
	private static final String restoredPrefix = "_restoredBy";
	
	// General header indices
	private static final int protocolI = 0;
	private static final int protocolVersionI = 1;
	private static final int senderI = 2;
	private static final int hashI = 3;
	private static final int chunkNoI = 4;
	private static final int repDegI = 5;
	
	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	
	// Sockets for multicast channels
	private ServiceChannel mcc;
	private ServiceChannel mdb;
	private ServiceChannel mdr;
	
	private ConcurrentHashMap<String, ProtocolState> protocols = new ConcurrentHashMap<String, ProtocolState>(8, 0.9f, 1);

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
		
		new Thread(null, this.mcc, "control channel").start();
		new Thread(null, this.mdb, "backup channel").start();
		new Thread(null, this.mdr, "recovery channel").start();
		
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
	
	/**
	 * Runs a specific protocol based on the protocol field of a service message.
	 * 
	 * @param fields the header fields
	 * @param packet the packet that originated the header fields
	 */
	public void runProtocol(DatagramPacket packet) throws IOException, InterruptedException {
		
		// Validate message as service message and extract its header
		ProtocolState state = new ProtocolState(new ServiceMessage());
		state.setPacket(packet);
		state.setFields(state.getParser().stripHeader(state.getPacket()));
		
		// Message was not recognised, ignore
		if(state.getFields() == null) return;
		
		// Run handler for each known protocol type
		switch(state.getFields()[protocolI].toUpperCase()) {
		
		// BACKUP protocol initiated
		case "PUTCHUNK":

			this.handlePutchunk(state);
			break;
			
		// BACKUP protocol response
		case "STORED":
			
			this.handleStored(state);
			break;
			
		// DELETE protocol initiated
		case "DELETE":
			
			this.handleDelete(state);
			break;
			
		// RESTORE protocol initiated
		case "GETCHUNK":
			
			this.handleGetchunk(state);
			break;
			
		// RESTORE protocol response
		case "CHUNK":
			
			this.handleChunk(state);
			break;
		}
	}

	// LATER update local database
	/**
	 * Handles BACKUP protocol by writing the received chunk only if it doesn't exist already and then sending
	 * a response STORED message. Doesn't write the chunk if the receiver and sender are the same.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handlePutchunk(ProtocolState state) throws IOException, InterruptedException {

		byte[] bodyData = state.getParser().stripBody(state.getPacket());
		
	    // Check if this Peer is the Peer that requested the backup
	    if(Integer.parseInt(state.getFields()[senderI]) == this.peerID) {
	    	SystemManager.getInstance().logPrint("own PUTCHUNK, ignoring", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
		
	    // Create file structure for this chunk
		String peerFolder = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix;
	    String chunkFolder = peerFolder + "/" + state.getFields()[hashI];
	    String chunkPath = chunkFolder + "/" + state.getFields()[chunkNoI];
	    this.createDirIfNotExists(peerFolder);
	    this.createDirIfNotExists(chunkFolder);
	    
	    // Write chunk data to file only if it doesn't already exist
	    File file = new File(chunkPath);
	    if(!file.exists()) {
	    	
	    	FileOutputStream output = new FileOutputStream(file);
	    	output.write(bodyData);
	    	output.close();
	    	
			SystemManager.getInstance().logPrint("written: " + state.getFields()[hashI] + "." + state.getFields()[chunkNoI], SystemManager.LogLevel.NORMAL);
	    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
	    
	    // Prepare the necessary fields for the response message
	    state.initBackupResponseState(this.protocolVersion, state.getFields()[hashI], state.getFields()[chunkNoI]);
	    
	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(minResponseWaitMS, maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);
	    Thread.sleep(waitTimeMS); // LATER use timeout on thread handler
	    
	    byte[] msg = state.getParser().createStoredMsg(this.peerID, state);
	    this.mcc.send(msg);
	}
	
	// LATER make map key be (this.id, senderID, SHA256, ProtocolType) -> ProtocolState
	/**
	 * Handles the responses to a BACKUP protocol by counting the unique STORED responses
	 * up to the desired replication degree for this protocol instance.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleStored(ProtocolState state) {
		
		// Check sender ID is different from this Peer's ID
		if(Integer.parseInt(state.getFields()[senderI]) == this.peerID) {
			SystemManager.getInstance().logPrint("own STORED, ignoring", SystemManager.LogLevel.DEBUG);
			return;
		}
		
		// Check if this BACKUP protocol exists
		ProtocolState currState = this.protocols.get("single");
		if(currState == null) {
			SystemManager.getInstance().logPrint("received STORED but no protocol matched", SystemManager.LogLevel.DEBUG);
			return;
		}

		// Add sender ID to set of peer IDs that have responded
		if(currState.getRespondedID().add(Integer.parseInt(state.getFields()[senderI])))
			SystemManager.getInstance().logPrint("added peer ID \"" + state.getFields()[senderI] + "\" to responded", SystemManager.LogLevel.DEBUG);
		
		// Check if the desired unique STORED messages have arrived and flag it if so
		int responseCount = currState.getRespondedID().size();
		int desiredCount = currState.getDesiredRepDeg();
		
		String respondedMsg = responseCount + " / " + desiredCount + " unique peers have responded";
		SystemManager.getInstance().logPrint(respondedMsg, SystemManager.LogLevel.DEBUG);

		if(responseCount >= desiredCount) currState.setStoredCountCorrect(true);
	}

	// LATER update local database
	/**
	 * Handles DELETE protocol by deleting all the chunks referring to this protocol's instance SHA256.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleDelete(ProtocolState state) {
		
	    // Open chunk folder for this hash and verify that it exists
		String peerFolder = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix;
	    String chunkFolder = peerFolder + "/" + state.getFields()[hashI];
	    
	    File folder = new File(chunkFolder);
	    if(!folder.exists()) {
		    SystemManager.getInstance().logPrint("don't have the file, ignoring message", SystemManager.LogLevel.DEBUG);
	    	return;
	    }

	    // Get all chunks and delete them
	    File[] chunks = folder.listFiles();
	    SystemManager.getInstance().logPrint("chunks to delete: " + chunks.length, SystemManager.LogLevel.DEBUG);
	    
	    for(File chunk : chunks) {
	    	chunk.delete();
	    }
	    
	    // Delete chunk folder
	    folder.delete();
	    SystemManager.getInstance().logPrint("deleted: " + state.getFields()[hashI], SystemManager.LogLevel.NORMAL);
	}
	
	// LATER update local database
	// LATER make map key be (this.id, senderID, SHA256, ProtocolType) -> ProtocolState
	/**
	 * Handles RESTORE protocol by checking local storage for requested SHA256 + chunkNo and sending
	 * the CHUNK message if found.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @throws IOException 
	 */
	private void handleGetchunk(ProtocolState state) throws IOException, InterruptedException {
		
	    // Construct relevant chunk path and verify that it exists in this Peer's storage
		String chunkPath = "./" + peerFolderPrefix + this.peerID + peerFolderSuffix + "/" + state.getFields()[hashI] + "/" + state.getFields()[chunkNoI];

	    File chunk = new File(chunkPath);
	    if(!chunk.exists()) {
		    SystemManager.getInstance().logPrint("don't have the file, ignoring message", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
	    
	    // Prepare the necessary fields for the response message
	    state.initRestoreResponseState(this.protocolVersion, state.getFields()[hashI], chunkPath, state.getFields()[chunkNoI]);
	    
	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(minResponseWaitMS, maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);
	    Thread.sleep(waitTimeMS); // LATER use timeout on thread handler
	    
	    byte[] msg = state.getParser().createChunkMsg(this.peerID, state);
	    this.mdr.send(msg);
	}
	
	// LATER update local database
	// LATER make map key be (this.id, senderID, SHA256, ProtocolType) -> ProtocolState
	/**
	 * Handles the responses to a RESTORE protocol by passing along the chunk data received.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleChunk(ProtocolState state) throws IOException {

		// Check if this RESTORE protocol exists
		ProtocolState currState = this.protocols.get("single");
		if(currState == null) {
			SystemManager.getInstance().logPrint("received CHUNK but no protocol matched", SystemManager.LogLevel.DEBUG);
			return;
		}

		// Store chunk number and chunk data received
		Long chunkNo = Long.parseLong(state.getFields()[chunkNoI]);
		byte[] data = state.getParser().stripBody(state.getPacket());
		
		SystemManager.getInstance().logPrint("restored chunk \"" + state.getFields()[hashI] + "." + chunkNo + "\"", SystemManager.LogLevel.DEBUG);
		currState.getRestoredChunks().put(chunkNo, data);
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

	/**
	 * Writes a set of chunk data to a single file. The file path is fixed and is based on the filename and PeerID.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @return whether the operation was successful
	 */
	private boolean writeRestoredChunks(ProtocolState state) throws IOException {
		
		// Check that all chunks were received
		ConcurrentHashMap<Long, byte[]> chunks = state.getRestoredChunks();
		if(chunks.size() != state.getChunkTotal()) return false;
		
		// Create filepaths for restored file
		String folderPath = "./" + restoredFolderName;
		String[] split = state.getFilename().split("[.]");
		
		String restoredFilepath;
		if(split.length <= 1) {
			restoredFilepath = folderPath + "/" + state.getFilename() + restoredPrefix + "_" + this.peerID;
		} else {
			split[split.length - 2] = split[split.length - 2] + restoredPrefix + "_" + this.peerID;
			restoredFilepath = folderPath + "/" + String.join(".", split);
		}
		
		SystemManager.getInstance().logPrint("restoring file in \"" + restoredFilepath + "\"", SystemManager.LogLevel.DEBUG);
		
		// Append all binary data to form restored file
		this.createDirIfNotExists("./" + restoredFolderName);

		File chunk = new File(restoredFilepath);
		FileOutputStream output = new FileOutputStream(chunk, true);
		for(Map.Entry<Long, byte[]> entry : chunks.entrySet()) {
			output.write(entry.getValue());
		}
		output.close();
		
		return true;
	}
	
	// LATER update local database
	@Override
	public void remoteBackup(String filepath, int repDeg) throws IOException, NoSuchAlgorithmException, InterruptedException {
		
		Thread.currentThread().setName("RMI");
		
		String backMsg = "backup: " + filepath + " - " + repDeg;
		SystemManager.getInstance().logPrint("started " + backMsg, SystemManager.LogLevel.NORMAL);

		// Initialise protocol state for backing up the file
		this.protocols.put("single",  new ProtocolState(ProtocolState.ProtocolType.BACKUP, new ServiceMessage()));
		ProtocolState state = this.protocols.get("single");
		
		if(!state.initBackupState(this.protocolVersion, filepath, repDeg)) {
			String backFileSize = "cannot backup files larger than 64GB!";
			SystemManager.getInstance().logPrint(backFileSize, SystemManager.LogLevel.NORMAL);
			return;
		}

		// Send PUTCHUNK and wait for repDeg STORED responses
		while(state.getAttempts() < maxAttempts) {
		
			// Prepare and send next PUTCHUNK message
			byte[] msg = state.getParser().createPutchunkMsg(this.peerID, state);
			this.mdb.send(msg);

			Thread.sleep((int) (baseTimeoutMS * Math.pow(2, state.getAttempts())));

			// Check if expected unique STORED messages were received
			if(state.isStoredCountCorrect()) {
				state.incrementCurrentChunkNo();
				state.resetStoredCount();
			} else {
				String timedOutMsg = "not enough STORED messages whithin " + (int) (baseTimeoutMS * Math.pow(2, state.getAttempts())) + " ms";
				SystemManager.getInstance().logPrint(timedOutMsg, SystemManager.LogLevel.DEBUG);
				state.incrementAttempts();
			}

			if(state.isFinished()) break;
		}

		// Finish protocol instance
		if(state.isFinished()) SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
		else {
			state.setFinished(true);
			SystemManager.getInstance().logPrint("failed " + backMsg, SystemManager.LogLevel.NORMAL);
		}
		
		this.protocols.remove("single");
	}
	
	// LATER update local database
	// LATER sliding window for GETCHUNK
	// LATER remove thread sleep
	@Override
	public void remoteRestore(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException {
		
		Thread.currentThread().setName("RMI");

		String resMsg = "restore: " + filepath;
		SystemManager.getInstance().logPrint("started " + resMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state for restoring file
		this.protocols.put("single",  new ProtocolState(ProtocolState.ProtocolType.RESTORE, new ServiceMessage()));
		ProtocolState state = this.protocols.get("single");

		if(!state.initRestoreState(this.protocolVersion, filepath)) {
			String backFileSize = "cannot restore files larger than 64GB!";
			SystemManager.getInstance().logPrint(backFileSize, SystemManager.LogLevel.NORMAL);
			return;
		}
		
		// Send all GETCHUNK messages
		while(!state.isFinished()) {
		
			// Create and send the GETCHUNK message for the current chunk
			byte[] msg = state.getParser().createGetchunkMsg(this.peerID, state);
			this.mcc.send(msg);

			Thread.sleep(restoreWaitMS);
			state.incrementCurrentChunkNo();
		}
		
		// Wait for message processing and then write restored file
		Thread.sleep(restoreChunkWaitMS);
		if(!this.writeRestoredChunks(state)) {
			SystemManager.getInstance().logPrint("not enough chunk data received", SystemManager.LogLevel.DEBUG);
			SystemManager.getInstance().logPrint("failed " + resMsg, SystemManager.LogLevel.NORMAL);
			return;
		}
		
		SystemManager.getInstance().logPrint("finished " + resMsg, SystemManager.LogLevel.NORMAL);
		this.protocols.remove("single");
	}

	// LATER use global protocol state for enhancement
	// LATER update local database
	@Override
	public void remoteDelete(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException {

		Thread.currentThread().setName("RMI");
		
		String delMsg = "delete: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state for deleting file
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.DELETE, new ServiceMessage());
		state.initDeleteState(this.protocolVersion, filepath);
		
		// Create and send DELETE message 3 times
		byte[] msg = state.getParser().createDeleteMsg(this.peerID, state);
		this.mcc.send(msg);
		Thread.sleep(deleteWaitMS);
		this.mcc.send(msg);
		Thread.sleep(deleteWaitMS);
		this.mcc.send(msg);
		
		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);
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
