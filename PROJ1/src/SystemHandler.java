import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SystemHandler implements Runnable {

	private DatagramPacket packet;
	private String channelName;
	
	public SystemHandler(DatagramPacket packet, String channelName) {
		this.packet = packet;
		this.channelName = channelName;
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName(this.channelName + " handler " + Thread.currentThread().getId());
		
		try {
			runProtocol(this.packet);
		} catch(IOException | InterruptedException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on receiver!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Runs a specific protocol based on the protocol field of a service message.
	 * 
	 * @param packet the packet containing the system message
	 */
	public void runProtocol(DatagramPacket packet) throws IOException, InterruptedException {
		
		// Validate message as service message and extract its header
		ProtocolState state = new ProtocolState(new ServiceMessage());
		Peer peer = Peer.getInstance();
		state.setPacket(packet);
		state.setFields(state.getParser().stripHeader(state.getPacket()));
		
		// Message was not recognised, ignore
		if(state.getFields() == null) return;
		
		// Run handler for each known protocol type
		switch(state.getFields()[Peer.protocolI].toUpperCase()) {
		
		// BACKUP protocol initiated
		case "PUTCHUNK":

			this.handlePutchunk(peer, state);
			break;
			
		// BACKUP protocol response
		case "STORED":
			
			this.handleStored(peer, state);
			break;
			
		// DELETE protocol initiated
		case "DELETE":
			
			this.handleDelete(peer, state);
			break;
			
		// RESTORE protocol initiated
		case "GETCHUNK":
			
			this.handleGetchunk(peer, state);
			break;
			
		// RESTORE protocol response
		case "CHUNK":
			
			this.handleChunk(peer, state);
			break;
		}
	}
	
	// LATER backup enh update database according to whether chunk was stored or not
	/**
	 * Handles BACKUP protocol by writing the received chunk only if it doesn't exist already and then sending
	 * a response STORED message. Doesn't write the chunk if the receiver and sender are the same.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handlePutchunk(Peer peer, ProtocolState state) throws IOException, InterruptedException {

		byte[] bodyData = state.getParser().stripBody(state.getPacket());
		
	    // Check if this Peer is the Peer that requested the backup
	    if(Integer.parseInt(state.getFields()[Peer.senderI]) == peer.getPeerID()) {
	    	SystemManager.getInstance().logPrint("own PUTCHUNK, ignoring", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
		
	    // Create file structure for this chunk
	    String storageFolder = "../" + Peer.storageFolderName;
		String peerFolder = storageFolder + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + state.getFields()[Peer.hashI];
	    String chunkPath = chunkFolder + "/" + state.getFields()[Peer.chunkNoI];
	    peer.createDirIfNotExists(storageFolder);
	    peer.createDirIfNotExists(peerFolder);
	    peer.createDirIfNotExists(chunkFolder);

	    // Write chunk data to file only if it doesn't already exist
	    File file = new File(chunkPath);
	    if(!file.exists()) {
	    	
	    	FileOutputStream output = new FileOutputStream(file);
	    	output.write(bodyData);
	    	output.close();
	    	
			SystemManager.getInstance().logPrint("written: " + state.getFields()[Peer.hashI] + "." + state.getFields()[Peer.chunkNoI], SystemManager.LogLevel.NORMAL);
	    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
	    
	    // Update local database
	    peer.getDatabase().putchunkUpdate(state, bodyData.length);
	    
	    // Prepare the necessary fields for the response message
	    state.initBackupResponseState(peer.getProtocolVersion(), state.getFields()[Peer.hashI], state.getFields()[Peer.chunkNoI]);
	    
	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(Peer.minResponseWaitMS, Peer.maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);

	    peer.getExecutor().schedule(new TimeoutHandler(state, ProtocolState.ProtocolType.BACKUP, this.channelName), waitTimeMS, TimeUnit.MILLISECONDS);
	}
	
	// LATER backup enh use new protocol type to store STORED in non initiators
	/**
	 * Handles the responses to a BACKUP protocol by counting the unique STORED responses
	 * up to the desired replication degree for this protocol instance.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleStored(Peer peer, ProtocolState state) {
		
		// Update database
		peer.getDatabase().storedUpdate(state);
		
		// Check sender ID is different from this Peer's ID
		if(Integer.parseInt(state.getFields()[Peer.senderI]) == peer.getPeerID()) {
			SystemManager.getInstance().logPrint("own STORED, ignoring", SystemManager.LogLevel.DEBUG);
			return;
		}
		
		// Check if this BACKUP protocol exists
		String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + ProtocolState.ProtocolType.BACKUP.name();
		ProtocolState currState = peer.getProtocols().get(protocolKey);
		if(currState == null) {
			SystemManager.getInstance().logPrint("received STORED but no protocol matched, key: " + protocolKey, SystemManager.LogLevel.DEBUG);
			return;
		}

		// Add sender ID to set of peer IDs that have responded
		if(currState.getRespondedID().add(Integer.parseInt(state.getFields()[Peer.senderI])))
			SystemManager.getInstance().logPrint("added peer ID \"" + state.getFields()[Peer.senderI] + "\" to responded", SystemManager.LogLevel.DEBUG);
		
		// Check if the desired unique STORED messages have arrived and flag it if so
		int responseCount = currState.getRespondedID().size();
		int desiredCount = currState.getDesiredRepDeg();
		
		String respondedMsg = responseCount + " / " + desiredCount + " unique peers have responded";
		SystemManager.getInstance().logPrint(respondedMsg, SystemManager.LogLevel.DEBUG);

		if(responseCount >= desiredCount) currState.setStoredCountCorrect(true);
	}

	// TODO update local database
	/**
	 * Handles DELETE protocol by deleting all the chunks referring to this protocol's instance SHA256.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleDelete(Peer peer, ProtocolState state) {
		
	    // Open chunk folder for this hash and verify that it exists
		String peerFolder = "../" + Peer.storageFolderName + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + state.getFields()[Peer.hashI];
	    
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
	    SystemManager.getInstance().logPrint("deleted: " + state.getFields()[Peer.hashI], SystemManager.LogLevel.NORMAL);
	    
	    //Update database
	    peer.getDatabase().deleteUpdate(state);
	}
	
	/**
	 * Handles RESTORE protocol by checking local storage for requested SHA256 + chunkNo and sending
	 * the CHUNK message if found.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleGetchunk(Peer peer, ProtocolState state) throws IOException, InterruptedException {
		
	    // Construct relevant chunk path and verify that it exists in this Peer's storage
		String chunkPath = "../" + Peer.storageFolderName + "/" + Peer.peerFolderPrefix + peer.getPeerID() + "/" + state.getFields()[Peer.hashI] + "/" + state.getFields()[Peer.chunkNoI];

	    File chunk = new File(chunkPath);
	    if(!chunk.exists()) {
		    SystemManager.getInstance().logPrint("don't have the file, ignoring message", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
	    
	    String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + state.getFields()[Peer.chunkNoI] + ProtocolState.ProtocolType.CHUNK_STOP.name();
	    if(peer.getProtocols().putIfAbsent(protocolKey, state) == null) {
	    	SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
	    }

	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(Peer.minResponseWaitMS, Peer.maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);

	    peer.getExecutor().schedule(new TimeoutHandler(state, ProtocolState.ProtocolType.RESTORE, this.channelName, protocolKey, chunkPath), waitTimeMS, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Handles the responses to a RESTORE protocol by passing along the chunk data received.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleChunk(Peer peer, ProtocolState state) throws IOException {

		// Check if a GETCHUNK for this CHUNK exists and if so set CHUNK already sent flag
	    String chunkKey = peer.getPeerID() + state.getFields()[Peer.hashI] + state.getFields()[Peer.chunkNoI] + ProtocolState.ProtocolType.CHUNK_STOP.name();
	    ProtocolState chunkState = peer.getProtocols().get(chunkKey);
	    
	    if(chunkState != null) {
	    	chunkState.setChunkMsgAlreadySent(true);
	    } else SystemManager.getInstance().logPrint("received CHUNK but no CHUNK_STOP protocol matched, key: " + chunkKey, SystemManager.LogLevel.VERBOSE);
	    
		// Check if this RESTORE protocol exists
		String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + ProtocolState.ProtocolType.RESTORE.name();
		ProtocolState currState = peer.getProtocols().get(protocolKey);
	    
		if(currState == null) {
			SystemManager.getInstance().logPrint("received CHUNK but no RESTORE protocol matched, key: " + protocolKey, SystemManager.LogLevel.DEBUG);
			return;
		}

		// Store chunk number and chunk data received
		Long chunkNo = Long.parseLong(state.getFields()[Peer.chunkNoI]);
		byte[] data = state.getParser().stripBody(state.getPacket());
		
		SystemManager.getInstance().logPrint("restored chunk \"" + state.getFields()[Peer.hashI] + "." + chunkNo + "\"", SystemManager.LogLevel.DEBUG);
		currState.getRestoredChunks().put(chunkNo, data);
	}
}
