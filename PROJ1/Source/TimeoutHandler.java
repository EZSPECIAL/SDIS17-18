import java.io.IOException;

public class TimeoutHandler implements Runnable {

	private ProtocolState state;
	private ProtocolState.ProtocolType type;
	private String channelName;
	private String chunkStopKey;
	private String chunkPath;
	
	/**
	 * A timeout handler for STORED messages. A STORED message relative to the ProtocolState instance is sent after
	 * a certain timeout has been reached.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param type the protocol type
	 * @param channelName the channel name
	 */
	public TimeoutHandler(ProtocolState state, ProtocolState.ProtocolType type, String channelName) {
		this.state = state;
		this.type = type;
		this.channelName = channelName;
	}
	
	/**
	 * A timeout handler for CHUNK messages. A CHUNK message relative to the ProtocolState instance is sent after
	 * a certain timeout has been reached.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param type the protocol type
	 * @param channelName the channel name
	 * @param chunkStopKey the key for the ProtocolState object handling unneeded CHUNK messages
	 * @param chunkPath the chunk path relevant to this operation
	 */
	public TimeoutHandler(ProtocolState state, ProtocolState.ProtocolType type, String channelName, String chunkStopKey, String chunkPath) {
		this.state = state;
		this.type = type;
		this.channelName = channelName;
		this.chunkStopKey = chunkStopKey;
		this.chunkPath = chunkPath;
	}

	@Override
	public void run() {
		
		Thread.currentThread().setName(this.channelName + " timeout " + Thread.currentThread().getId());
		
		Peer peer = Peer.getInstance();

		switch(this.type) {

		// Send STORED
		case BACKUP:
			
			try {
				this.sendStoredMsg(peer);
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception on scheduled STORED!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
			break;

		// Send CHUNK
		case RESTORE:
			
			try {
				this.sendChunkMsg(peer);
			} catch (IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception on scheduled CHUNK!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
			break;

		default:
			break;
		}
	}
	
	/**
	 * Sends a STORED message relevant to this protocol instance.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void sendStoredMsg(Peer peer) throws IOException {
		
		byte[] msg = this.state.getParser().createStoredMsg(peer.getPeerID(), state);
		peer.getMcc().send(msg);
	}
	
	/**
	 * Sends a CHUNK message relevant to this protocol instance. CHUNK message is only sent if no other
	 * CHUNK message is received for the same SHA256.chunkNo combination.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void sendChunkMsg(Peer peer) throws IOException {
		
	    // Check if CHUNK message for the same chunk was received to avoid flooding of CHUNK messages
	    ProtocolState chunkState = peer.getProtocols().get(this.chunkStopKey);
	    if(chunkState.isChunkMsgAlreadySent()) {
	    	SystemManager.getInstance().logPrint("detected CHUNK message for same chunk, aborting...", SystemManager.LogLevel.DEBUG);
		    peer.getProtocols().remove(this.chunkStopKey);
		    SystemManager.getInstance().logPrint("key removed: " + this.chunkStopKey, SystemManager.LogLevel.VERBOSE);
		    return;
	    }
	    
	    peer.getProtocols().remove(this.chunkStopKey);
	    SystemManager.getInstance().logPrint("key removed: " + this.chunkStopKey, SystemManager.LogLevel.VERBOSE);
	    
	    // Prepare the necessary fields for the response message and send it
	    state.initRestoreResponseState(peer.getProtocolVersion(), state.getFields()[Peer.hashI], this.chunkPath, state.getFields()[Peer.chunkNoI]);
	    byte[] msg = this.state.getParser().createChunkMsg(peer.getPeerID(), state);
	    peer.getMdr().send(msg);
	}

}
