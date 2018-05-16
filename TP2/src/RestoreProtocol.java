import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RestoreProtocol implements Runnable {

	private static final int consecutiveMsgCount = 5;
	
	private ServerSocket server;
	private String filepath;
	private String restoredFilepath;
	private long receivedChunks = 0;

	/**
	 * Runs a RESTORE protocol procedure with specified filepath.
	 * 
	 * @param filepath the file path to restore
	 */
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
		} catch(NoSuchAlgorithmException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		if(key == null) {
			SystemManager.getInstance().logPrint("cannot restore files larger than 64GB!", SystemManager.LogLevel.NORMAL);
			return;
		}
		
		ProtocolState state = peer.getProtocols().get(key);

		// GETCHUNK message loop
		try {
			
			this.createRestoredPath(peer, state);
			
			if(this.getchunkLoop(peer, state)) {
				SystemManager.getInstance().logPrint("finished " + resMsg, SystemManager.LogLevel.NORMAL);
			} else {
				
				// Delete partial file
				this.deleteRestoredFile(peer, state);
				
				SystemManager.getInstance().logPrint("not enough chunk data received", SystemManager.LogLevel.DEBUG);
				SystemManager.getInstance().logPrint("failed " + resMsg, SystemManager.LogLevel.NORMAL);
			}
			
			// Close TCP server if enhanced Peer
			if(!peer.getProtocolVersion().equals("1.0")) {
				if(this.server != null) this.server.close();
			}
			
		} catch(InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			peer.getProtocols().remove(key);
			SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
			return;
		}
		
		SystemManager.getInstance().logPrint("threads end: " + Thread.activeCount(), SystemManager.LogLevel.DEBUG); // TODO remove later
		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
	}
	
	/**
	 * Submits thread that runs the server for this enhanced RESTORE protocol.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void runRestoreServer(Peer peer) {
		
		SystemManager.getInstance().logPrint("threads start: " + Thread.activeCount(), SystemManager.LogLevel.DEBUG); // TODO remove later
		
		// Run server if it doesn't exist
		if(this.server != null) return;
		
		try {

			this.server = new ServerSocket((Peer.restoreBasePort + (peer.getPeerID() - 1) * 10) + 1); // TODO add to Peer as function
		} catch (IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception creating server socket!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		peer.getExecutor().submit(new RestoreServer(this.server, consecutiveMsgCount));
	}

	/**
	 * Initialises the ProtocolState object relevant to this restore procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return whether initialisation was successful
	 */
	private String initializeProtocolInstance(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.RESTORE, new ServiceMessage());
		
		if(!state.initRestoreState(peer.getProtocolVersion(), this.filepath)) return null;
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name();
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}
	
	/**
	 * Sends the required GETCHUNK messages for backing up a file and then waits on reception of all
	 * the relevant CHUNK messages.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @return whether the restore was successful
	 */
	private boolean getchunkLoop(Peer peer, ProtocolState state) throws IOException, InterruptedException {
		
		while(!state.isFinished()) {

			// Run TCP server if enhanced Peer
			if(!peer.getProtocolVersion().equals("1.0")) {
				this.runRestoreServer(peer);
			}
			
			int sent = 0;
			while(sent < consecutiveMsgCount && !state.isFinished()) {
			
				// Create and send the GETCHUNK message for the current chunk
				byte[] msg = state.getParser().createGetchunkMsg(peer.getPeerID(), state);
				peer.getMcc().send(msg);

				Thread.sleep(Peer.consecutiveMsgWaitMS);
				state.incrementCurrentChunkNo();
				sent++;
			}

			if(!checkReceivedChunks(state)) return false;
			
			// Write file as chunks arrive and reset hash map
			this.writeRestoredChunks(peer, state);
			state.setRestoredChunks(new ConcurrentHashMap<Long, byte[]>(8, 0.9f, 1));
		}

		return true;
	}
	
	/**
	 * Checks received chunk data hash map size to verify that expected CHUNK messages
	 * have been received.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @return whether enough CHUNK messages were received
	 */
	private boolean checkReceivedChunks(ProtocolState state) {
		
		ConcurrentHashMap<Long, byte[]> chunks;
		
		// Find the chunk limit for this batch
		long diff = state.getChunkTotal() - this.receivedChunks;
		long limit = (diff > RestoreProtocol.consecutiveMsgCount) ? RestoreProtocol.consecutiveMsgCount : diff;
		
		do {
			if(Thread.interrupted()) return false;
			chunks = state.getRestoredChunks();
		} while(chunks.size() < limit);
		
		this.receivedChunks += limit;
		return true;
	}
	
	/**
	 * Writes a set of chunk data to a single file using the file path created previously.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void writeRestoredChunks(Peer peer, ProtocolState state) throws IOException {
		
		ConcurrentHashMap<Long, byte[]> chunks = state.getRestoredChunks();

		// Append binary data to form restored file
		File chunk = new File(this.restoredFilepath);
		FileOutputStream output = new FileOutputStream(chunk, true);
		for(Map.Entry<Long, byte[]> entry : chunks.entrySet()) {
			output.write(entry.getValue());
		}
		output.close();
		
		SystemManager.getInstance().logPrint("restored " + chunks.size() + " chunks", SystemManager.LogLevel.VERBOSE);
	}
	
	/**
	 * Creates the file path for restoring a file.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void createRestoredPath(Peer peer, ProtocolState state) throws IOException {
		
		// Create filepaths for restored file
		String folderPath = "../" + Peer.restoredFolderName;
		String[] split = state.getFilename().split("[.]");
		
		// Get file's access time to build the restored filename
		Path attrRead = Paths.get(this.filepath);
	    FileTime access = (FileTime) Files.getAttribute(attrRead, "lastAccessTime");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		String dateString = String.format(dateFormat.format(access.toMillis()));
		
		String restoredFilepath;
		if(split.length <= 1) {
			restoredFilepath = folderPath + "/" + dateString + " - " + state.getFilename() + Peer.restoredSuffix + "_" + peer.getPeerID();
		} else {
			split[split.length - 2] = split[split.length - 2] + Peer.restoredSuffix + "_" + peer.getPeerID();
			restoredFilepath = folderPath + "/" + dateString + " - " + String.join(".", split);
		}

		peer.createDirIfNotExists(folderPath);
		this.restoredFilepath = restoredFilepath;
		
		SystemManager.getInstance().logPrint("restoring file in \"" + restoredFilepath + "\"", SystemManager.LogLevel.DEBUG);
	}
	
	/**
	 * Deletes a restored file using the file path created previously.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 */
	private void deleteRestoredFile(Peer peer, ProtocolState state) {
		
		File restored = new File(this.restoredFilepath);
		if(restored.exists()) restored.delete();
	}
}
