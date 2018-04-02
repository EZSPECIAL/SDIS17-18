import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RestoreProtocol implements Runnable {

	private String filepath;
	
	public RestoreProtocol(String filepath) {
		this.filepath = filepath;
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("Restore " + Thread.currentThread().getId());

		String resMsg = "restore: " + filepath;
		SystemManager.getInstance().logPrint("started " + resMsg, SystemManager.LogLevel.NORMAL);
		
		Peer peer = Peer.getInstance();

		// Initialise protocol state
		String key = null;
		try {
			key = this.initializeProtocolInstance(peer);
		} catch (NoSuchAlgorithmException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
		
		if(key == null) {
			SystemManager.getInstance().logPrint("cannot restore files larger than 64GB!", SystemManager.LogLevel.NORMAL);
			return;
		}
		
		ProtocolState state = peer.getProtocols().get(key);
		
		// GETCHUNK message loop
		try {
			if(this.getchunkLoop(peer, state)) SystemManager.getInstance().logPrint("finished " + resMsg, SystemManager.LogLevel.NORMAL);
			else {
				SystemManager.getInstance().logPrint("not enough chunk data received", SystemManager.LogLevel.DEBUG);
				SystemManager.getInstance().logPrint("failed " + resMsg, SystemManager.LogLevel.NORMAL);
			}
		} catch (InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}

		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
	}

	// DOC
	private String initializeProtocolInstance(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.RESTORE, new ServiceMessage());
		
		if(!state.initRestoreState(peer.getProtocolVersion(), this.filepath)) return null;
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name();
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}
	
	// DOC
	private boolean getchunkLoop(Peer peer, ProtocolState state) throws IOException, InterruptedException {
		
		while(!state.isFinished()) {
		
			// Create and send the GETCHUNK message for the current chunk
			byte[] msg = state.getParser().createGetchunkMsg(peer.getPeerID(), state);
			peer.getMcc().send(msg);

			Thread.sleep(Peer.restoreWaitMS); // LATER reduce this after worker threads implementation
			state.incrementCurrentChunkNo();
		}
		
		// Wait until all CHUNK messages arrive and then write file
		return this.writeRestoredChunks(peer, state);
	}
	
	/**
	 * Writes a set of chunk data to a single file. The file path is fixed and is based on the filename and PeerID.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @return whether the operation was successful
	 */
	private boolean writeRestoredChunks(Peer peer, ProtocolState state) throws IOException {
		
		// Check that all chunks were received
		ConcurrentHashMap<Long, byte[]> chunks;
		do {
			if(Thread.interrupted()) return false;
			chunks = state.getRestoredChunks();
		} while(chunks.size() != state.getChunkTotal());

		// Create filepaths for restored file
		String folderPath = "./" + Peer.restoredFolderName;
		String[] split = state.getFilename().split("[.]");
		
		String restoredFilepath;
		if(split.length <= 1) {
			restoredFilepath = folderPath + "/" + state.getFilename() + Peer.restoredPrefix + "_" + peer.getPeerID();
		} else {
			split[split.length - 2] = split[split.length - 2] + Peer.restoredPrefix + "_" + peer.getPeerID();
			restoredFilepath = folderPath + "/" + String.join(".", split);
		}
		
		SystemManager.getInstance().logPrint("restoring file in \"" + restoredFilepath + "\"", SystemManager.LogLevel.DEBUG);
		
		// Append all binary data to form restored file
		peer.createDirIfNotExists("./" + Peer.restoredFolderName);

		File chunk = new File(restoredFilepath);
		FileOutputStream output = new FileOutputStream(chunk, true);
		for(Map.Entry<Long, byte[]> entry : chunks.entrySet()) {
			output.write(entry.getValue());
		}
		output.close();
		
		return true;
	}
}
