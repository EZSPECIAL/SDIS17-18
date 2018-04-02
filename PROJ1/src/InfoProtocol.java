import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfoProtocol implements Runnable {
	
	// TODO add initiated files
	// TODO add system chunks
	@Override
	public void run() {
		
		Peer peer = Peer.getInstance();
		ConcurrentHashMap<String, ChunkInfo> chunks = peer.getDatabase().getChunks();
		
		this.printStored(chunks);
		// this.printSystemChunks(chunks);
		// this.printLocalFiles();
	}
	
	/**
	 * Prints currently stored chunks for this Peer.
	 * 
	 * @param chunks the map containing info about the currently stored chunks
	 */
	private void printStored(ConcurrentHashMap<String, ChunkInfo> chunks) {
		
		for(Map.Entry<String, ChunkInfo> entry : chunks.entrySet()) {
			ChunkInfo chunk = entry.getValue();
			int size = chunk.getSize();
			if(size < 0) continue;
			SystemManager.getInstance().simpleLog("CHUNK", SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tid: " + chunk.getId(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tperceived repDeg: " + chunk.getPerceivedRepDeg(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tdesired repDeg: " + chunk.getDesiredRepDeg(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tsize: " + chunk.getSize() + "KB", SystemManager.LogLevel.NORMAL);
		}
	}
}
