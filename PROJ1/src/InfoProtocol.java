import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfoProtocol implements Runnable {
	
	// TODO print disk status
	@Override
	public void run() {
		
		Peer peer = Peer.getInstance();
		ConcurrentHashMap<String, ChunkInfo> chunks = peer.getDatabase().getChunks();
		ConcurrentHashMap<String, FileInfo> files = peer.getDatabase().getInitiatedFiles();
		
		this.printInitiated(files);
		this.printStored(chunks);
		this.printSystemChunks(chunks);
		//this.printDiskSpace();
	}
	
	/**
	 * Prints the file backups initiated by this Peer.
	 * 
	 * @param files the map containing info about the initiated backups
	 */
	private void printInitiated(ConcurrentHashMap<String, FileInfo> files) {
		
		for(Map.Entry<String, FileInfo> fileEntry : files.entrySet()) {
			
			FileInfo fileInfo = fileEntry.getValue();
			SystemManager.getInstance().simpleLog("INITIATED FILE", SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tpath: " + fileInfo.getFilepath(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tSHA256: " + fileInfo.getFileID(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tdesired repDeg: " + fileInfo.getDesiredRepDeg(), SystemManager.LogLevel.NORMAL);
		}
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
			
			SystemManager.getInstance().simpleLog("STORED CHUNK", SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tid: " + chunk.getId(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tperceived repDeg: " + chunk.getPerceivedRepDeg().size(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tdesired repDeg: " + chunk.getDesiredRepDeg(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tsize: " + chunk.getSize() + "KB", SystemManager.LogLevel.NORMAL);
		}
	}
	
	/**
	 * Prints perceived system chunks for this Peer.
	 * 
	 * @param chunks the map containing info about the perceived system chunks
	 */
	private void printSystemChunks(ConcurrentHashMap<String, ChunkInfo> chunks) {
		
		for(Map.Entry<String, ChunkInfo> entry : chunks.entrySet()) {
			
			ChunkInfo chunk = entry.getValue();
			int size = chunk.getSize();
			if(size >= 0) continue;
			
			SystemManager.getInstance().simpleLog("SYSTEM CHUNK", SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tid: " + chunk.getId(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tperceived repDeg: " + chunk.getPerceivedRepDeg().size(), SystemManager.LogLevel.NORMAL);
		}
	}
}
