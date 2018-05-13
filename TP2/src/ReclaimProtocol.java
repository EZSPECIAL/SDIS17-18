import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReclaimProtocol implements Runnable {

	@Override
	public void run() {

		Thread.currentThread().setName("Reclaim " + Thread.currentThread().getId());
		
		Peer peer = Peer.getInstance();
		
		long currDisk = peer.getUsedSpace();
		long maxDisk = peer.getMaxDiskSpace();
		
		// Check if current disk usage is exceeding limit
		SystemManager.getInstance().logPrint(currDisk + "KB used out of " + maxDisk + "KB", SystemManager.LogLevel.DEBUG);
		if(maxDisk >= currDisk) {
			return;
		}
		
		String reclMsg = "reclaim: " + maxDisk + "KB";
		SystemManager.getInstance().logPrint("started " + reclMsg, SystemManager.LogLevel.NORMAL);
		
		try {
			this.deleteChunks(peer);
		} catch (IOException | InterruptedException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		SystemManager.getInstance().logPrint("finished " + reclMsg, SystemManager.LogLevel.NORMAL);
	}
	
	/**
	 * Deletes chunks to free up space until used space is below limit. Starts by trying to
	 * delete chunks that have higher replication degree than needed and then deletes sequentially
	 * if space used is still above limit.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void deleteChunks(Peer peer) throws IOException, InterruptedException {
		
		ConcurrentHashMap<String, ConcurrentHashMap<Long, ChunkInfo>> chunks = peer.getDatabase().getChunks();
		boolean finishedF = false;
		
		// Try to erase chunks whose perceived replication degree is higher than the desired
		for(Map.Entry<String, ConcurrentHashMap<Long, ChunkInfo>> hashEntry : chunks.entrySet()) {
			
			String hash = hashEntry.getKey();
			
			for(Map.Entry<Long, ChunkInfo> chunkEntry : hashEntry.getValue().entrySet()) {
				
				ChunkInfo chunk = chunkEntry.getValue();
				int size = chunk.getSize();
				if(size < 0) continue;
				
				// Handle chunk deletion
				if(chunk.getPerceivedRepDeg().size() > chunk.getDesiredRepDeg()) {
					
					String peerFolder = "../" + Peer.storageFolderName + "/" + Peer.peerFolderPrefix + peer.getPeerID();
				    String chunkPath = peerFolder + "/" + hash + "/" + chunkEntry.getKey();
				    
				    File file = new File(chunkPath);
				    if(file.exists()) {
				    	file.delete();
				    	chunk.setSize(-1);
				    	chunk.getPerceivedRepDeg().remove(peer.getPeerID());
				    	SystemManager.getInstance().logPrint("updated chunk " + hash + "." + chunkEntry.getKey() + " with new perceived repDeg " + chunk.getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
				    	SystemManager.getInstance().logPrint("removed " + hash + "." + chunkEntry.getKey(), SystemManager.LogLevel.DEBUG);
				    	
				    	// Create and send REMOVED message
				    	ProtocolState state = new ProtocolState(new ServiceMessage());
				    	state.initRemovedState(peer.getProtocolVersion(), hash, chunkEntry.getKey().toString());
				    	byte[] msg = state.getParser().createRemovedMsg(peer.getPeerID(), state);
				    	peer.getMcc().send(msg);
				    	Thread.sleep(Peer.consecutiveMsgWaitMS);
				    }
				}
				
				if(peer.getMaxDiskSpace() >= peer.getUsedSpace()) {
					finishedF = true;
					break;
				}
			}
			
			if(finishedF) break;
		}
		
		if(finishedF) return;
		SystemManager.getInstance().logPrint("not enough chunks to remove, removing randomly", SystemManager.LogLevel.DEBUG);
		
		// Erase chunks sequentially until space usage is below limit
		for(Map.Entry<String, ConcurrentHashMap<Long, ChunkInfo>> hashEntry : chunks.entrySet()) {
			
			String hash = hashEntry.getKey();
			
			for(Map.Entry<Long, ChunkInfo> chunkEntry : hashEntry.getValue().entrySet()) {
				
				ChunkInfo chunk = chunkEntry.getValue();
				int size = chunk.getSize();
				if(size < 0) continue;

				String peerFolder = "../" + Peer.storageFolderName + "/" + Peer.peerFolderPrefix + peer.getPeerID();
				String chunkPath = peerFolder + "/" + hash + "/" + chunkEntry.getKey();

				File file = new File(chunkPath);
				if(file.exists()) {
					file.delete();
					chunk.setSize(-1);
					chunk.getPerceivedRepDeg().remove(peer.getPeerID());
					SystemManager.getInstance().logPrint("updated chunk " + hash + "." + chunkEntry.getKey() + " with new perceived repDeg " + chunk.getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
					SystemManager.getInstance().logPrint("removed " + hash + "." + chunkEntry.getKey(), SystemManager.LogLevel.DEBUG);
					
					// Create and send REMOVED message
			    	ProtocolState state = new ProtocolState(new ServiceMessage());
			    	state.initRemovedState(peer.getProtocolVersion(), hash, chunkEntry.getKey().toString());
			    	byte[] msg = state.getParser().createRemovedMsg(peer.getPeerID(), state);
			    	peer.getMcc().send(msg);
			    	Thread.sleep(Peer.consecutiveMsgWaitMS);
				}
				
				if(peer.getMaxDiskSpace() >= peer.getUsedSpace()) {
					finishedF = true;
					break;
				}
			}
			
			if(finishedF) break;
		}
	}
}