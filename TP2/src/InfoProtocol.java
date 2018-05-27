import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfoProtocol implements Runnable {

	@Override
	public void run() {
		
		Peer peer = Peer.getInstance();
		SystemDatabase db = peer.getDatabase();
		
		ConcurrentHashMap<String, ConcurrentHashMap<Long, ChunkInfo>> chunks = db.getChunks();
		ConcurrentHashMap<String, FileInfo> files = db.getInitiatedFiles();
		ConcurrentHashMap<Integer, HashSet<String>> toDelete = db.getFilesToDelete();
		
		this.printInitiated(files);
		this.printStored(chunks);
		this.printSystemChunks(chunks);
		this.printDiskUsage();
		this.printDeletionList(toDelete);
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
			SystemManager.getInstance().simpleLog("\tfilename: " + fileInfo.getFilename(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tchunks: " + fileInfo.getTotalChunks(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tSHA256: " + fileInfo.getFileID(), SystemManager.LogLevel.NORMAL);
			SystemManager.getInstance().simpleLog("\tdesired repDeg: " + fileInfo.getDesiredRepDeg(), SystemManager.LogLevel.NORMAL);
		}
	}
	
	/**
	 * Prints currently stored chunks for this Peer.
	 * 
	 * @param chunks the map containing info about the currently stored chunks
	 */
	private void printStored(ConcurrentHashMap<String, ConcurrentHashMap<Long, ChunkInfo>> chunks) {
		
		for(Map.Entry<String, ConcurrentHashMap<Long, ChunkInfo>> hashEntry : chunks.entrySet()) {
			
			for(Map.Entry<Long, ChunkInfo> chunkEntry : hashEntry.getValue().entrySet()) {
				
				ChunkInfo chunk = chunkEntry.getValue();
				int size = chunk.getSize();
				if(size < 0) continue;
				
				SystemManager.getInstance().simpleLog("STORED CHUNK", SystemManager.LogLevel.NORMAL);
				SystemManager.getInstance().simpleLog("\tid: " + chunk.getId(), SystemManager.LogLevel.NORMAL);
				this.printPerceivedRepDeg(chunk.getPerceivedRepDeg());
				SystemManager.getInstance().simpleLog("\tdesired repDeg: " + chunk.getDesiredRepDeg(), SystemManager.LogLevel.NORMAL);
				SystemManager.getInstance().simpleLog("\tsize: " + chunk.getSize() + "KB", SystemManager.LogLevel.NORMAL);
			}
		}
	}
	
	/**
	 * Prints perceived system chunks for this Peer.
	 * 
	 * @param chunks the map containing info about the perceived system chunks
	 */
	private void printSystemChunks(ConcurrentHashMap<String, ConcurrentHashMap<Long, ChunkInfo>> chunks) {
		
		for(Map.Entry<String, ConcurrentHashMap<Long, ChunkInfo>> hashEntry : chunks.entrySet()) {

			for(Map.Entry<Long, ChunkInfo> chunkEntry : hashEntry.getValue().entrySet()) {

				ChunkInfo chunk = chunkEntry.getValue();
				int size = chunk.getSize();
				if(size >= 0) continue;

				SystemManager.getInstance().simpleLog("SYSTEM CHUNK", SystemManager.LogLevel.NORMAL);
				SystemManager.getInstance().simpleLog("\tid: " + chunk.getId(), SystemManager.LogLevel.NORMAL);
				this.printPerceivedRepDeg(chunk.getPerceivedRepDeg());
			}
		}
	}
	
	/**
	 * Prints the peers that the current Peer believes to have a specific chunk.
	 * 
	 * @param peers the peers that have this chunk
	 */
	private void printPerceivedRepDeg(ConcurrentHashMap<Integer, Integer> peers) {
		
		// Print which Peer IDs this Peer believes to have the chunk
		String perceivedRepDeg = "\tperceived repDeg: " + peers.size();

		if(peers.size() > 0) perceivedRepDeg += " (";
		for(Map.Entry<Integer, Integer> peerIDs : peers.entrySet()) {
			perceivedRepDeg += " " + peerIDs.getKey();
		}
		if(peers.size() > 0) perceivedRepDeg += " )";
		
		SystemManager.getInstance().simpleLog(perceivedRepDeg, SystemManager.LogLevel.NORMAL);
	}

	/**
	 * Prints the files that should be deleted from each Peer.
	 * 
	 * @param files the map containing info about the Peers that have files that should be deleted
	 */
	private void printDeletionList(ConcurrentHashMap<Integer, HashSet<String>> files) {
		
		SystemManager.getInstance().simpleLog("DEBUG DELETE ENH", SystemManager.LogLevel.DEBUG);
		
		for(Map.Entry<Integer, HashSet<String>> peer : files.entrySet()) {
			
			SystemManager.getInstance().simpleLog("Peer " + peer.getKey(), SystemManager.LogLevel.DEBUG);
			for(String value : peer.getValue()) {
				SystemManager.getInstance().simpleLog("\t" + value, SystemManager.LogLevel.DEBUG);
			}
		}
	}
	
	/**
	 * Prints the used disk space compared to the maximum disk space usage allowed.
	 */
	private void printDiskUsage() {
		
		Peer peer = Peer.getInstance();
		SystemManager.getInstance().simpleLog(peer.getUsedSpace() + "KB used out of " + peer.getMaxDiskSpace() + "KB", SystemManager.LogLevel.NORMAL);
	}
}
