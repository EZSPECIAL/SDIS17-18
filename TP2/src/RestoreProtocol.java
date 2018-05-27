import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RestoreProtocol implements Runnable {

	private static final int consecutiveMsgCount = 5;
	
	private ServerSocket server;
	private int serverPort;
	private String filepath;
	private FileInfo fileInfo;
	private boolean found;
	private String restoredFilepath;
	private long receivedChunks = 0;
	private long currentStartChunkNo = 0;
	private long timeElapsed = 0;
	private long timeoutMS = 0;

	/**
	 * Runs a RESTORE protocol procedure with specified filepath.
	 * 
	 * @param filepath the file path to restore
	 * @param fileInfo the object containing info about the file
	 * @param found whether the file was initiated on this Peer
	 */
	public RestoreProtocol(String filepath, FileInfo fileInfo, boolean found) {
		this.filepath = filepath;
		this.fileInfo = fileInfo;
		this.found = found;
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("Restore " + Thread.currentThread().getId());
		
		Peer peer = Peer.getInstance();
		
		// Peer is not the initiator, try to retrieve file info from other Peers
		if(!found) {
			
			String retrieveKey = this.initializeRetrieveProtocol(peer);
			ProtocolState retrieveState = peer.getProtocols().get(retrieveKey);
			
			// Send request for file info
			try {
				byte[] msg = retrieveState.getParser().createRetrieveMsg(peer.getPeerID(), peer.getProtocolVersion(), this.filepath);
				peer.getMcc().send(msg);
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception retrieving info!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
			
			// Wait for response
			do {
				if(Thread.interrupted()) {
					SystemManager.getInstance().logPrint("could not retrieve file info, might not exist in the system", SystemManager.LogLevel.NORMAL);
					return;
				}

			} while(!retrieveState.isRetrieveMsgResponded());
			
			this.fileInfo = retrieveState.getFileInfo();
			
			peer.getProtocols().remove(retrieveKey);
			SystemManager.getInstance().logPrint("key removed: " + retrieveKey, SystemManager.LogLevel.VERBOSE);
			
			this.timeoutMS = fileInfo.getTotalChunks() * Peer.baseRestoreTimeoutMS;
		}
		
		String resMsg = "restore: " + filepath;
		SystemManager.getInstance().logPrint("started " + resMsg, SystemManager.LogLevel.NORMAL);

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
			
		} catch(IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on restore protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			peer.getProtocols().remove(key);
			SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
			return;
		}

		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
	}
	
	/**
	 * Submits thread that runs the server for this enhanced RESTORE protocol.
	 * 
	 * @param peer the singleton Peer instance
	 */
	private void runRestoreServer(Peer peer) {
		
		// Run server if it doesn't exist
		if(this.server != null) return;
		
		try {

			this.serverPort = Peer.getInstance().findNextPortAllowed();
			this.server = new ServerSocket(this.serverPort);
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
	 * @return the protocol key, null if unsuccessful
	 */
	private String initializeProtocolInstance(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.RESTORE, new ServiceMessage());
		
		if(!state.initRestoreState(peer.getProtocolVersion(), this.fileInfo)) return null;
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name();
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}
	
	/**
	 * Initialises the ProtocolState object relevant to this retrieve procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return the protocol key, null if unsuccessful
	 */
	private String initializeRetrieveProtocol(Peer peer) {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.RETRIEVE, new ServiceMessage());
		
		String protocolKey = peer.getPeerID() + this.filepath + state.getProtocolType().name();
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
	private boolean getchunkLoop(Peer peer, ProtocolState state) throws IOException {
		
		while(!state.isFinished()) {

			// Run TCP server if enhanced Peer
			if(!peer.getProtocolVersion().equals("1.0")) {
				this.runRestoreServer(peer);
			}
			
			// Starting point for the next batch
			this.currentStartChunkNo = state.getCurrentChunkNo();
			
			int sent = 0;
			while(sent < consecutiveMsgCount && !state.isFinished()) {
			
				// Create and send the GETCHUNK message for the current chunk, according to protocol version
				byte[] msg;
				if(peer.getProtocolVersion().equals("1.0")) {
					msg = state.getParser().createGetchunkMsg(peer.getPeerID(), state);
				} else {
					msg = state.getParser().createEnhGetchunkMsg(peer.getPeerID(), state, this.serverPort);
				}
				peer.getMcc().send(msg);

				try {
					Thread.sleep(Peer.consecutiveMsgWaitMS);
				} catch(InterruptedException e) {
					// ignore
				}
				
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
		
		boolean valid = true;
		do {
			
            long timeBefore = System.currentTimeMillis();
			
			if(Thread.interrupted()) {
				if(this.timeoutMS != 0 && this.timeElapsed >= this.timeoutMS) return false;
			}
			
			valid = true;
			chunks = state.getRestoredChunks();
			
			// Check that every expected chunk arrived
			for(long i = this.currentStartChunkNo; i < this.currentStartChunkNo + limit; i++) {
				
				if(!chunks.containsKey(i)) valid = false;
			}
			
            timeElapsed = (System.currentTimeMillis() - timeBefore);
		} while(!valid);
		
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
			SystemManager.getInstance().logPrint("restored chunk no: " + entry.getKey(), SystemManager.LogLevel.VERBOSE);
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
		String[] split = this.fileInfo.getFilename().split("[.]");
		
		// Get current date/time
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss"); 
	    Date date = new Date();  
	    String dateString = dateFormat.format(date);
		
		String restoredFilepath;
		if(split.length <= 1) {
			restoredFilepath = folderPath + "/" + dateString + " - " + this.fileInfo.getFilename() + Peer.restoredSuffix + "_" + peer.getPeerID();
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
