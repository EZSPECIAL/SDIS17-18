import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class TimeoutHandler implements Runnable {

	private ProtocolState state;
	private ProtocolState.ProtocolType type;
	private String channelName;
	private String stopKey;
	private String chunkPath;
	private int desiredRepDeg;
	
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
	 * @param desiredRepDeg desired replication degree
	 */
	public TimeoutHandler(ProtocolState state, ProtocolState.ProtocolType type, String channelName, String stopKey, int desiredRepDeg) {
		this.state = state;
		this.type = type;
		this.channelName = channelName;
		this.stopKey = stopKey;
		this.desiredRepDeg = desiredRepDeg;
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
		
		// Check if desired replication degree has already been met if message is enhanced as well as Peer
		if(!this.state.getFields()[Peer.protocolVersionI].equals("1.0") && !peer.getProtocolVersion().equals("1.0")) {
			if(this.handleEnhancedBackup(peer, this.state)) {
				SystemManager.getInstance().logPrint("ignoring PUTCHUNK since replication degree has been met", SystemManager.LogLevel.DEBUG);
				return;
			}
		} else SystemManager.getInstance().logPrint("not running enhanced BACKUP as message and Peer were not both enhanced", SystemManager.LogLevel.DEBUG);
	    
	    byte[] bodyData = this.state.getParser().stripBody(this.state.getPacket());
	    
		// Check if a RECLAIM for this PUTCHUNK exists and if so set PUTCHUNK already sent flag
	    String chunkKey = peer.getPeerID() + this.state.getFields()[Peer.hashI] + this.state.getFields()[Peer.chunkNoI] + ProtocolState.ProtocolType.RECLAIM.name();
	    ProtocolState chunkState = peer.getProtocols().get(chunkKey);
	    
	    if(chunkState != null) {
	    	chunkState.setPutchunkMsgAlreadySent(true);
	    } else SystemManager.getInstance().logPrint("received PUTCHUNK but no RECLAIM protocol matched, key: " + chunkKey, SystemManager.LogLevel.VERBOSE);
	    
	    // Create file structure for this chunk
	    String storageFolder = "../" + Peer.storageFolderName;
		String peerFolder = storageFolder + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + this.state.getFields()[Peer.hashI];
	    String chunkPath = chunkFolder + "/" + this.state.getFields()[Peer.chunkNoI];
	    peer.createDirIfNotExists(storageFolder);
	    peer.createDirIfNotExists(peerFolder);
	    peer.createDirIfNotExists(chunkFolder);

	    // Write chunk data to file only if it doesn't already exist
	    File file = new File(chunkPath);
	    if(!file.exists()) {
	    	
	    	FileOutputStream output = new FileOutputStream(file);
	    	output.write(bodyData);
	    	output.close();
	    	
			SystemManager.getInstance().logPrint("written: " + this.state.getFields()[Peer.hashI] + "." + this.state.getFields()[Peer.chunkNoI], SystemManager.LogLevel.NORMAL);
	    } else SystemManager.getInstance().logPrint("chunk already stored", SystemManager.LogLevel.DEBUG);
	    
	    // Update local database
	    peer.getDatabase().putchunkUpdate(this.state, bodyData.length);
	    peer.getExecutor().execute(new ReclaimProtocol());
	    	    
	    // Prepare the necessary fields for the response message
	    this.state.initBackupResponseState(peer.getProtocolVersion(), this.state.getFields()[Peer.hashI], this.state.getFields()[Peer.chunkNoI]);
		
		byte[] msg = this.state.getParser().createStoredMsg(peer.getPeerID(), this.state);
		peer.getMcc().send(msg);
	}

	/**
	 * Handles enhanced BACKUP protocol by checking if desired replication degree has
	 * already been met by other Peers. Sends STORED response in case Peer already stores
	 * the chunk.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @return whether storing of chunk data should be aborted
	 */
	private boolean handleEnhancedBackup(Peer peer, ProtocolState state) throws IOException {
	    
		String hashKey = state.getFields()[Peer.hashI];
		long chunkHashkey = Long.parseLong(state.getFields()[Peer.chunkNoI]);
		
		// Check that file hash exists
		if(!peer.getDatabase().getChunks().containsKey(hashKey)) {
			SystemManager.getInstance().logPrint("no data about " + hashKey, SystemManager.LogLevel.DATABASE);
			return false;
		}

		ConcurrentHashMap<Long, ChunkInfo> chunksInfo = peer.getDatabase().getChunks().get(hashKey);
		
		// Check that chunk exists
		if(!chunksInfo.containsKey(chunkHashkey)) {
	    	SystemManager.getInstance().logPrint("no data about " + hashKey + "." + chunkHashkey, SystemManager.LogLevel.DATABASE);
	    	return false;
		}
		
		ChunkInfo chunk = chunksInfo.get(chunkHashkey);
		int desiredRepDeg = Integer.parseInt(state.getFields()[Peer.repDegI]);
		int perceivedRepDeg = chunk.getPerceivedRepDeg().size();
		
		// Update desired repDeg of this chunk
		chunk.setDesiredRepDeg(desiredRepDeg);
		
		SystemManager.getInstance().logPrint("perceived " + perceivedRepDeg + " chunk copies out of " + desiredRepDeg + " desired", SystemManager.LogLevel.DEBUG);

		if(perceivedRepDeg >= desiredRepDeg) {
			
		    // Check if chunk is stored on this Peer
		    String chunkPath = this.getChunkPath(peer, state);
		    File file = new File(chunkPath);
		    if(file.exists() && file.isFile()) {
		    	
			    this.state.initBackupResponseState(peer.getProtocolVersion(), this.state.getFields()[Peer.hashI], this.state.getFields()[Peer.chunkNoI]);
				
				byte[] msg = this.state.getParser().createStoredMsg(peer.getPeerID(), this.state);
				peer.getMcc().send(msg);
		    }
			
			return true;
		} else return false;
	}

	/**
	 * Returns where a chunk should be stored using the current Peer and ProtocolState.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @return the chunk path
	 */
	private String getChunkPath(Peer peer, ProtocolState state) {
	
	    String storageFolder = "../" + Peer.storageFolderName;
		String peerFolder = storageFolder + "/" + Peer.peerFolderPrefix + peer.getPeerID();
	    String chunkFolder = peerFolder + "/" + this.state.getFields()[Peer.hashI];
	    String chunkPath = chunkFolder + "/" + this.state.getFields()[Peer.chunkNoI];
		
	    return chunkPath;
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
	    this.state.initReclaimState(peer.getProtocolVersion(), state.getFields()[Peer.hashI], state.getFields()[Peer.chunkNoI], chunkPath, this.desiredRepDeg);
	    byte[] msg = this.state.getParser().createReclaimMsg(peer.getPeerID(), state);
	    peer.getMdb().send(msg);
	}
}
