import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeleteProtocol implements Runnable {

	private static final int maxAttempts = 3;
	
	private String filepath;
	private int attempts = 0;
	
	/**
	 * Runs a DELETE protocol procedure with specified filepath.
	 * 
	 * @param filepath the file path to delete
	 */
	public DeleteProtocol(String filepath) {
		this.filepath = filepath;
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
		
		String delMsg = "delete enh: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state
		String key = this.initializeProtocolInstance(peer);
		ProtocolState state = peer.getProtocols().get(key);
		
		// Create set of unique peers that have the file
		HashSet<Integer> peersWithFile = new HashSet<Integer>();
		ConcurrentHashMap<Long, ChunkInfo> chunks = peer.getDatabase().getChunks().get(state.getHashHex());
		
		for(Map.Entry<Long, ChunkInfo> chunk : chunks.entrySet()) {
			peersWithFile.addAll(chunk.getValue().getPerceivedRepDeg().keySet());
		}
		
		SystemManager.getInstance().logPrint("unique peers with file: " + peersWithFile.size(), SystemManager.LogLevel.VERBOSE);

		// Send DELETE messages
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
			if(state.getRespondedID().get(0L).containsAll(peersWithFile)) break;

			SystemManager.getInstance().logPrint("not enough DELETED messages whithin " + timeoutMS + "ms", SystemManager.LogLevel.DEBUG);
		}
		
		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);
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
}