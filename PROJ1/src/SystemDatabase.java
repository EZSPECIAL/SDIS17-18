import java.util.concurrent.ConcurrentHashMap;

public class SystemDatabase {

	private ConcurrentHashMap<String, ChunkInfo> chunks = new ConcurrentHashMap<String, ChunkInfo>(8, 0.9f, 1);

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
	    	SystemManager.getInstance().logPrint("updating chunk \"" + key + "\" with new repDeg " + repDeg, SystemManager.LogLevel.DATABASE);
		}
    	SystemManager.getInstance().logPrint("new chunk \"" + key + "\" with repDeg " + repDeg + " and " + size + "KB", SystemManager.LogLevel.DATABASE);
	}
	
	/**
	 * @return the hash map containing info about chunks in the system
	 */
	public ConcurrentHashMap<String, ChunkInfo> getChunks() {
		return chunks;
	}

	/**
	 * @param the hash map containing info about chunks in the system
	 */
	public void setChunks(ConcurrentHashMap<String, ChunkInfo> chunks) {
		this.chunks = chunks;
	}

}
