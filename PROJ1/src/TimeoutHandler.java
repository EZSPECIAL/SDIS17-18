import java.io.IOException;

public class TimeoutHandler implements Runnable {

	private ProtocolState state;
	private ProtocolState.ProtocolType type;
	private String channelName;
	private String stopKey;
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
	 * A timeout handler for PUTCHUNK messages. A PUTCHUNK message relative to the ProtocolState instance is sent after
	 * a certain timeout has been reached.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param type the protocol type
	 * @param channelName the channel name
	 * @param stopKey the key for the ProtocolState object handling unneeded PUTCHUNK messages
	 */
	public TimeoutHandler(ProtocolState state, ProtocolState.ProtocolType type, String channelName, String stopKey) {
		this.state = state;
		this.type = type;
		this.channelName = channelName;
		this.stopKey = stopKey;
	}
	
	/**
	 * A timeout handler for CHUNK messages. A CHUNK message relative to the ProtocolState instance is sent after
	 * a certain timeout has been reached.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param type the protocol type
	 * @param channelName the channel name
	 * @param stopKey the key for the ProtocolState object handling unneeded CHUNK messages
	 * @param chunkPath the chunk path relevant to this operation
	 */
	public TimeoutHandler(ProtocolState state, ProtocolState.ProtocolType type, String channelName, String stopKey, String chunkPath) {
		this.state = state;
		this.type = type;
		this.channelName = channelName;
		this.stopKey = stopKey;
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
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception on scheduled CHUNK!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
			break;

		// Send PUTCHUNK
		case RECLAIM:
			
			try {
				this.sendPutchunkMsg(peer);
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception on scheduled PUTCHUNK!", SystemManager.LogLevel.NORMAL);
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
	    ProtocolState chunkState = peer.getProtocols().get(this.stopKey);
	    if(chunkState.isChunkMsgAlreadySent()) {
	    	SystemManager.getInstance().logPrint("detected CHUNK message for same chunk, aborting...", SystemManager.LogLevel.DEBUG);
		    peer.getProtocols().remove(this.stopKey);
		    SystemManager.getInstance().logPrint("key removed: " + this.stopKey, SystemManager.LogLevel.VERBOSE);
		    return;
	    }
	    
	    peer.getProtocols().remove(this.stopKey);
	    SystemManager.getInstance().logPrint("key removed: " + this.stopKey, SystemManager.LogLevel.VERBOSE);
	    
	    // Prepare the necessary fields for the response message and send it
	    this.state.initRestoreResponseState(peer.getProtocolVersion(), state.getFields()[Peer.hashI], this.chunkPath, state.getFields()[Peer.chunkNoI]);
	    byte[] msg = this.state.getParser().createChunkMsg(peer.getPeerID(), state);
	    peer.getMdr().send(msg);
	}
	
	/**
	 * Sends a PUTCHUNK message relevant to this protocol instance. PUTCHUNK message is only sent if no other
	 * PUTCHUNK message is received for the same SHA256.chunkNo combination.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void sendPutchunkMsg(Peer peer) throws IOException {
		
	    // Check if PUTCHUNK message for the same chunk was received to avoid flooding of PUTCHUNK messages
	    ProtocolState chunkState = peer.getProtocols().get(this.stopKey);
	    if(chunkState.isPutchunkMsgAlreadySent()) {
	    	SystemManager.getInstance().logPrint("detected PUTCHUNK message for same chunk, aborting...", SystemManager.LogLevel.DEBUG);
		    peer.getProtocols().remove(this.stopKey);
		    SystemManager.getInstance().logPrint("key removed: " + this.stopKey, SystemManager.LogLevel.VERBOSE);
		    return;
	    }
	    
	    peer.getProtocols().remove(this.stopKey);
	    SystemManager.getInstance().logPrint("key removed: " + this.stopKey, SystemManager.LogLevel.VERBOSE);
	    
	    // Create path to chunk
	    String storageFolder = "../" + Peer.storageFolderName;
		String peerFolder = storageFolder + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + state.getFields()[Peer.hashI];
	    String chunkPath = chunkFolder + "/" + state.getFields()[Peer.chunkNoI];
	    
	    // Prepare the necessary fields for the response message and send it
	    this.state.initReclaimState(peer.getProtocolVersion(), state.getFields()[Peer.hashI], state.getFields()[Peer.chunkNoI], chunkPath);
	    byte[] msg = this.state.getParser().createReclaimMsg(peer.getPeerID(), state);
	    peer.getMdb().send(msg);
	}
}
