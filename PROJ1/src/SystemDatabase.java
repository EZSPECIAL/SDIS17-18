import java.util.concurrent.ConcurrentHashMap;

public class SystemDatabase {

	private ConcurrentHashMap<String, ChunkInfo> chunks = new ConcurrentHashMap<String, ChunkInfo>(8, 0.9f, 1);
	private ConcurrentHashMap<String, FileInfo> initiatedFiles = new ConcurrentHashMap<String, FileInfo>(8, 0.9f, 1);

	/**
	 * Updates the database with the received PUTCHUNK message info about a chunk.
	 * Inserts new chunk info if it didn't exist or updates desired replication degree
	 * if already existed.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param size the size of the chunk in KB
	 */
	public void putchunkUpdate(ProtocolState state, int size) {
		
		String key = state.getFields()[Peer.hashI] + "." + state.getFields()[Peer.chunkNoI];
		int repDeg = Integer.parseInt(state.getFields()[Peer.repDegI]);
		if(this.chunks.putIfAbsent(key, new ChunkInfo(key, repDeg, size)) != null) {
			this.chunks.get(key).setDesiredRepDeg(repDeg);
	    	SystemManager.getInstance().logPrint("updated chunk \"" + key + "\" with new desired repDeg " + repDeg, SystemManager.LogLevel.DATABASE);
	    	return;
		}
    	SystemManager.getInstance().logPrint("new chunk \"" + key + "\" with desired repDeg " + repDeg + " and " + size + "KB", SystemManager.LogLevel.DATABASE);
	}
	
	// DOC
	public void storedUpdate(ProtocolState state) {
		
		String key = state.getFields()[Peer.hashI] + "." + state.getFields()[Peer.chunkNoI];
		
		if(this.chunks.putIfAbsent(key, new ChunkInfo(key)) != null) {
			this.chunks.get(key).getPerceivedRepDeg().put(Integer.parseInt(state.getFields()[Peer.senderI]), 0);
	    	SystemManager.getInstance().logPrint("updated chunk \"" + key + "\" with new perceived repDeg " + this.chunks.get(key).getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
	    	return;
		}
		
		this.chunks.get(key).getPerceivedRepDeg().put(Integer.parseInt(state.getFields()[Peer.senderI]), 0);
    	SystemManager.getInstance().logPrint("new chunk \"" + key + "\" with perceived repDeg " + this.chunks.get(key).getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
	}
	
	//DOC
	public void deleteUpdate(ProtocolState state) {
		
		String fileKey = state.getFields()[Peer.hashI];
		
		//this.initiatedFiles.remove(fileKey)
	}
	
	/**
	 * Updates the database with the initiated backup. Inserts new file info
	 * and current chunk info. If file info already exists updates the file path and if
	 * chunk already exists updates desired replication degree.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	public void backupUpdate(ProtocolState state) {
		
		// Update database with file info
		String hash = state.getHashHex();
		String filepath = state.getFilepath();
		int repDeg = state.getDesiredRepDeg();
		String fileKey = hash;
		
		if(this.initiatedFiles.putIfAbsent(fileKey, new FileInfo(filepath, hash, repDeg)) != null) {
			this.initiatedFiles.get(fileKey).setFilepath(filepath);
			this.initiatedFiles.get(fileKey).setDesiredRepDeg(repDeg);
			SystemManager.getInstance().logPrint("updated file \"" + fileKey + "\" with path " + filepath + " and desired repDeg " + repDeg, SystemManager.LogLevel.DATABASE);
		} else SystemManager.getInstance().logPrint("new file \"" + fileKey + "\" with path " + filepath + " and desired repDeg " + repDeg, SystemManager.LogLevel.DATABASE);
	}
	
	/**
	 * @return the hash map containing info about chunks in the system
	 */
	public ConcurrentHashMap<String, ChunkInfo> getChunks() {
		return chunks;
	}

	/**
	 * @param chunks the hash map containing info about chunks in the system
	 */
	public void setChunks(ConcurrentHashMap<String, ChunkInfo> chunks) {
		this.chunks = chunks;
	}

	/**
	 * @return the hash map containing info about backed up files by this peer
	 */
	public ConcurrentHashMap<String, FileInfo> getInitiatedFiles() {
		return initiatedFiles;
	}

	/**
	 * @param initiatedFiles the hash map containing info about backed up files by this peer
	 */
	public void setInitiatedFiles(ConcurrentHashMap<String, FileInfo> initiatedFiles) {
		this.initiatedFiles = initiatedFiles;
	}

}
