import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeleteProtocol implements Runnable {

	private static final int maxAttempts = 3;
	
	private boolean pendingDelete = false;
	private String filepath;
	private String hash;
	private int pendingPeerID;
	private int attempts = 0;
	
	/**
	 * Runs a DELETE protocol procedure with specified filepath.
	 * 
	 * @param filepath the file path to delete
	 */
	public DeleteProtocol(String filepath) {
		this.filepath = filepath;
	}
	
	/**
	 * Runs a pending DELETE protocol procedure with specified pending Peer ID and hash.
	 * 
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 * @param pendingPeerID peer ID pending deletion of file
	 */
	public DeleteProtocol(String hash, int pendingPeerID) {
		this.hash = hash;
		this.pendingPeerID = pendingPeerID;
		this.pendingDelete = true;
	}
	
	@Override
	public void run() {

		Thread.currentThread().setName("Delete " + Thread.currentThread().getId());
		Peer peer = Peer.getInstance();
		
		// Run protocol according to version
		if(peer.getProtocolVersion().equals("1.0")) {
			this.normalDelete(peer);
		} else {
			try {
				this.enhancedDelete(peer);
			} catch(IOException | InterruptedException | NoSuchAlgorithmException e) {
				SystemManager.getInstance().logPrint("I/O Exception or thread interruption on delete protocol!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
		}
	}
	
	/**
	 * Runs a delete protocol which attempts to delete a file from the backup service.
	 * Does not confirm deletion.
	 * 
	 * @param peer the singleton Peer instance
	 */
	public void normalDelete(Peer peer) {
		
		String delMsg = "delete: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state for deleting file
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.DELETE, new ServiceMessage());
		try {
			state.initDeleteState(peer.getProtocolVersion(), filepath);
		} catch(NoSuchAlgorithmException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception on delete protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		// Create and send DELETE message 3 times
		byte[] msg = state.getParser().createDeleteMsg(peer.getPeerID(), state);
		
		try {
			peer.getMcc().send(msg);
			Thread.sleep(Peer.consecutiveMsgWaitMS);
			peer.getMcc().send(msg);
			Thread.sleep(Peer.consecutiveMsgWaitMS);
			peer.getMcc().send(msg);
		} catch(InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on delete protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}

		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);
	}
	
	/**
	 * Runs an enhanced delete protocol which attempts to delete a file from the backup service.
	 * Confirms deletion by waiting for the expected number of responses.
	 * 
	 * @param peer the singleton Peer instance
	 */
	public void enhancedDelete(Peer peer) throws IOException, InterruptedException, NoSuchAlgorithmException {
		
		String delMsg = this.pendingDelete ? "pending delete: " + this.hash : "delete enh: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state
		String key;
		if(this.pendingDelete) key = this.initializePendingDelete(peer);
		else key = this.initializeProtocolInstance(peer);
		
		ProtocolState state = peer.getProtocols().get(key);
		HashSet<Integer> peersWithFile = new HashSet<Integer>();
		
		// Create set of unique peers that have the file using database
		if(!this.pendingDelete) {
			
			ConcurrentHashMap<Long, ChunkInfo> chunks = peer.getDatabase().getChunks().get(state.getHashHex());

			if(chunks != null) {
				for(Map.Entry<Long, ChunkInfo> chunk : chunks.entrySet()) {
					peersWithFile.addAll(chunk.getValue().getPerceivedRepDeg().keySet());
				}
			}
		// If pending delete use the pending Peer ID
		} else {
			peersWithFile.add(this.pendingPeerID);
		}
		
		SystemManager.getInstance().logPrint("unique peers with file: " + peersWithFile.size(), SystemManager.LogLevel.VERBOSE);
		
		// Send DELETE messages and wait for confirmations if file exists on backup service
		if(peersWithFile.size() != 0) {
			
			if(!this.deleteLoop(peer, state, peersWithFile)) {

				SystemManager.getInstance().logPrint("not all expected peers responsed to DELETE, storing missing confirmation for later", SystemManager.LogLevel.NORMAL);

				// Add to database the Peers that haven't responded to this DELETE request
				peersWithFile.removeAll(state.getRespondedID().get(0L));
				peer.getDatabase().addToDelete(peersWithFile, state.getHashHex());
			} else {
				if(this.pendingDelete) {
					peer.getDatabase().removeFromDelete(this.pendingPeerID, this.hash);
				}
			}
		} else SystemManager.getInstance().logPrint("file does not seem to be backed up, cancelling deletion", SystemManager.LogLevel.NORMAL);
		
		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);
	}
	
	/**
	 * Sends DELETE messages and waits for the expected Peers to confirm deletion.
	 * Resends a set number of times and on time out adds the remaining Peers to
	 * the database so they can be sent the DELETE message later.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @param peersWithFile peers that have the file
	 * @return whether enough deletion confirmations were received
	 */
	private boolean deleteLoop(Peer peer, ProtocolState state, HashSet<Integer> peersWithFile) throws IOException, InterruptedException {
		
		while(this.attempts < DeleteProtocol.maxAttempts) {

			// Prepare and send next DELETE message
			byte[] msg = state.getParser().createDeleteMsg(peer.getPeerID(), state);
			peer.getMcc().send(msg);
			int timeoutMS = (int) (Peer.baseTimeoutMS * Math.pow(2, this.attempts));
			Thread.sleep(timeoutMS);

			this.attempts++;
			
			// Check current response status
			int responseCount = state.getRespondedID().get(0L).size();
			int desiredCount = peersWithFile.size();
			
			String respondedMsg = responseCount + " / " + desiredCount + " unique peers have confirmed deletion";
			SystemManager.getInstance().logPrint(respondedMsg, SystemManager.LogLevel.DEBUG);
			
			// Finish if responded Peer IDs and expected Peer IDs match
			if(state.getRespondedID().get(0L).containsAll(peersWithFile)) return true;

			SystemManager.getInstance().logPrint("not enough DELETED messages whithin " + timeoutMS + "ms", SystemManager.LogLevel.DEBUG);
		}
		
		return false;
	}
	
	/**
	 * Initialises the ProtocolState object relevant to this delete procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return the key relevant to this protocol
	 */
	private String initializeProtocolInstance(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.DELETE, new ServiceMessage());
		
		state.initDeleteState(peer.getProtocolVersion(), filepath);
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name();
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}

	/**
	 * Initialises the ProtocolState object relevant to this pending delete procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return the key relevant to this protocol
	 */
	private String initializePendingDelete(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.DELETE, new ServiceMessage());
		
		state.initPendingDeleteState(peer.getProtocolVersion(), this.hash);
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name() + this.pendingPeerID;
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}
}