import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
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
		} catch(IOException | InterruptedException | NoSuchAlgorithmException e) {
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
	public void runProtocol(DatagramPacket packet) throws IOException, InterruptedException, NoSuchAlgorithmException {
		
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
			
		// DELETE protocol response for enhanced DELETE
		case "DELETED":
			
			this.handleDeleted(peer, state);
			break;
			
		// RESTORE protocol initiated
		case "GETCHUNK":
			
			this.handleGetchunk(peer, state);
			break;
			
		// RESTORE protocol response
		case "CHUNK":
			
			this.handleChunk(peer, state);
			break;
			
		// RECLAIM protocol initiated
		case "REMOVED":
			
			this.handleRemoved(peer, state);
			break;
			
		// Peer started message for DELETE protocol enhancement
		case "STARTED":
			
			this.handleStarted(peer, state);
			break;
		}
	}

	/**
	 * Handles BACKUP protocol by writing the received chunk only if it doesn't exist already and then sending
	 * a response STORED message. Doesn't write the chunk if the receiver and sender are the same.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handlePutchunk(Peer peer, ProtocolState state) throws IOException, InterruptedException {

	    // Check if this Peer is the Peer that requested the backup
	    if(Integer.parseInt(state.getFields()[Peer.senderI]) == peer.getPeerID()) {
	    	SystemManager.getInstance().logPrint("own PUTCHUNK, ignoring", SystemManager.LogLevel.DEBUG);
	    	return;
	    }

	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(Peer.minResponseWaitMS, Peer.maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);

	    peer.getExecutor().schedule(new TimeoutHandler(state, ProtocolState.ProtocolType.BACKUP, this.channelName), waitTimeMS, TimeUnit.MILLISECONDS);
	}

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
		
		// Add sender ID to set of peer IDs that have responded to this chunk's backup
		int senderID = Integer.parseInt(state.getFields()[Peer.senderI]);
		long currChunk = Long.parseLong(state.getFields()[Peer.chunkNoI]);
		if(currState.getRespondedID().get(currChunk).add(senderID)) {
			SystemManager.getInstance().logPrint("added peer ID \"" + senderID + "\" to responded for chunk " + currChunk, SystemManager.LogLevel.DEBUG);
		}

		// Print the current status
		int responseCount = currState.getRespondedID().get(currChunk).size();
		int desiredCount = currState.getDesiredRepDeg();
		
		String respondedMsg = responseCount + " / " + desiredCount + " unique peers for chunk " + currChunk;
		SystemManager.getInstance().logPrint(respondedMsg, SystemManager.LogLevel.DEBUG);
	}

	/**
	 * Handles DELETE protocol by deleting all the chunks referring to this protocol's instance SHA256.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleDelete(Peer peer, ProtocolState state) throws NoSuchAlgorithmException, IOException {
		
	    //Update database
	    peer.getDatabase().deleteUpdate(state);
		
	    // Open chunk folder for this hash and verify that it exists
		String peerFolder = "../" + Peer.storageFolderName + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + state.getFields()[Peer.hashI];
	    
	    File folder = new File(chunkFolder);
	    
	    if(!folder.exists()) {
		    SystemManager.getInstance().logPrint("don't have the file, ignoring message", SystemManager.LogLevel.DEBUG);
	    } else {

	    	// Get all chunks and delete them
	    	File[] chunks = folder.listFiles();
	    	SystemManager.getInstance().logPrint("chunks to delete: " + chunks.length, SystemManager.LogLevel.DEBUG);

	    	for(File chunk : chunks) {
	    		chunk.delete();
	    	}

	    	// Delete chunk folder
	    	folder.delete();

	    	SystemManager.getInstance().logPrint("deleted: " + state.getFields()[Peer.hashI], SystemManager.LogLevel.NORMAL);
	    }
	    
	    if(peer.getProtocolVersion().equals("1.0")) return;
	    
	    // Reply with DELETED if running enhanced version
	    ProtocolState responseState = new ProtocolState(new ServiceMessage());
	    responseState.initDeleteResponseState(peer.getProtocolVersion(), state.getFields()[Peer.hashI]);
	    
	    byte[] msg = state.getParser().createDeletedMsg(peer.getPeerID(), responseState);
	    peer.getMcc().send(msg);
	}
	
	/**
	 * Handles the responses to an enhanced DELETE protocol by counting the unique DELETED responses
	 * up to the expected number of responses.
	 *
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleDeleted(Peer peer, ProtocolState state) {
		
		// Check if pending DELETE protocol exists
		String pendingDeleteKey = peer.getPeerID() + state.getFields()[Peer.hashI] + ProtocolState.ProtocolType.DELETE.name() + state.getFields()[Peer.senderI];
		ProtocolState currState = peer.getProtocols().get(pendingDeleteKey);
		
		if(currState == null) {
			
			SystemManager.getInstance().logPrint("received DELETED but no pending delete matched, key: " + pendingDeleteKey, SystemManager.LogLevel.DEBUG);
			
			// Check if DELETE protocol exists
			String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + ProtocolState.ProtocolType.DELETE.name();
			currState = peer.getProtocols().get(protocolKey);
			if(currState == null) {
				SystemManager.getInstance().logPrint("received DELETED but no protocol matched, key: " + protocolKey, SystemManager.LogLevel.DEBUG);
				return;
			}
		}
		
		// Add sender ID to set of peer IDs that have responded to this deletion
		int senderID = Integer.parseInt(state.getFields()[Peer.senderI]);
		if(currState.getRespondedID().get(0L).add(senderID)) {
			SystemManager.getInstance().logPrint("added peer ID \"" + senderID + "\" to responded", SystemManager.LogLevel.DEBUG);
		}
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
	    
	    // Create CHUNK_STOP protocol for stopping unneeded CHUNK messages
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
	    
	    // Abort storing chunk data if both sending and current Peer are enhanced
	    if(!state.getFields()[Peer.protocolVersionI].equals("1.0") && !peer.getProtocolVersion().equals("1.0")) {
	    	SystemManager.getInstance().logPrint("not storing CHUNK because enhanced RESTORE", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
	    
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
	
	/**
	 * Handles RECLAIM protocol by checking if replication degree falls below the desired one.
	 * If so starts a PUTCHUNK to try and raise the replication degree for this chunk.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleRemoved(Peer peer, ProtocolState state) {
		
	    // Check if this Peer is the Peer that sent the message
	    if(Integer.parseInt(state.getFields()[Peer.senderI]) == peer.getPeerID()) {
	    	SystemManager.getInstance().logPrint("own REMOVED, ignoring", SystemManager.LogLevel.DEBUG);
	    	return;
	    }
	    
	    // Update database
	    int desiredRepDeg = peer.getDatabase().removedUpdate(state);
	    if(desiredRepDeg < 0) return;
	    
	    // Wait a random millisecond delay from a previously specified range and then send the message
	    int waitTimeMS = ThreadLocalRandom.current().nextInt(Peer.minResponseWaitMS, Peer.maxResponseWaitMS + 1);
	    SystemManager.getInstance().logPrint("waiting " + waitTimeMS + "ms", SystemManager.LogLevel.DEBUG);
	    
	    // Create RECLAIM protocol for stopping unneeded PUTCHUNK messages
	    String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + state.getFields()[Peer.chunkNoI] + ProtocolState.ProtocolType.RECLAIM.name();
	    if(peer.getProtocols().putIfAbsent(protocolKey, state) == null) {
	    	SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
	    }
	    
	    peer.getExecutor().schedule(new TimeoutHandler(state, ProtocolState.ProtocolType.RECLAIM, this.channelName, protocolKey, desiredRepDeg), waitTimeMS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Handles pending DELETE protocol by checking if the started Peer has pending deletions
	 * on this Peer. Starts a new DELETE for each pending deletion.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void handleStarted(Peer peer, ProtocolState state) {
		
		// Check that started Peer and current Peer are enhanced
		if(state.getFields()[Peer.protocolVersionI].equals("1.0") || peer.getProtocolVersion().equals("1.0")) {
	    	SystemManager.getInstance().logPrint("started Peer or current Peer is not enhanced, not running pending DELETEs", SystemManager.LogLevel.DEBUG);
			return;
		}
		
		int senderID = Integer.parseInt(state.getFields()[Peer.senderI]);
		HashSet<String> filesToDelete = peer.getDatabase().getFilesToDelete().get(senderID);
		
		// Check if there are files pending deletion
		if(filesToDelete == null) {
	    	SystemManager.getInstance().logPrint("no pending DELETEs for Peer " + senderID, SystemManager.LogLevel.DEBUG);
			return;
		} else if(filesToDelete.size() == 0) {
	    	SystemManager.getInstance().logPrint("no pending DELETEs for Peer " + senderID, SystemManager.LogLevel.DEBUG);
			return;
		}

		// Run pending DELETE for each file the started Peer has yet to delete
		for(String hash : filesToDelete) {
			peer.getExecutor().submit(new DeleteProtocol(hash, senderID));
		}
	}
}
