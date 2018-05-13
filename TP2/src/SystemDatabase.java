import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class SystemDatabase implements Serializable, Runnable {

	private static final long serialVersionUID = -3900468368934039133L;
	private static final long backupDelay = 5000;

	private ConcurrentHashMap<String, ConcurrentHashMap<Integer, ChunkInfo>> chunks = new ConcurrentHashMap<String, ConcurrentHashMap<Integer, ChunkInfo>>(8, 0.9f, 1);
	private ConcurrentHashMap<String, FileInfo> initiatedFiles = new ConcurrentHashMap<String, FileInfo>(8, 0.9f, 1);

	/**
	 * Backs up the current database to file.
	 */
	synchronized void saveDatabase() throws IOException {
		
		String databaseFolder = "../" + Peer.databaseFolderName;
		Peer.getInstance().createDirIfNotExists(databaseFolder);
		String databasePath = databaseFolder + "/" + Peer.databasePrefix + Peer.getInstance().getPeerID();
		
		FileOutputStream file = new FileOutputStream(databasePath);
		ObjectOutputStream output = new ObjectOutputStream(file);
		output.writeObject(this);
		
		output.close();
		file.close();
		
		SystemManager.getInstance().logPrint("Saved database", SystemManager.LogLevel.VERBOSE);
	}

	/**
	 * Loads a backed up database.
	 * 
	 * @param databasePath the path of the database to load
	 * @return the database object loaded, null if no database file was found
	 */
	static synchronized SystemDatabase loadDatabase(String databasePath) throws ClassNotFoundException, IOException {
		
		File db = new File(databasePath);
		if(db.exists() && db.isFile()) {
			
			FileInputStream file = new FileInputStream(databasePath);
			ObjectInputStream input = new ObjectInputStream(file);
			
			SystemDatabase database = (SystemDatabase) input.readObject();
			
			input.close();
			file.close();
			
			SystemManager.getInstance().logPrint("Loaded database", SystemManager.LogLevel.NORMAL);
			return database;
		} else return null;
	}
		
	/**
	 * Updates the database with the received PUTCHUNK message info about a chunk.
	 * Inserts new chunk info if it didn't exist or updates desired replication degree
	 * if already existed.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @param size the size of the chunk in KB
	 */
	public void putchunkUpdate(ProtocolState state, int size) {
		
		String hashKey = state.getFields()[Peer.hashI];
		int chunkKey = Integer.parseInt(state.getFields()[Peer.chunkNoI]);
		
		this.chunks.putIfAbsent(hashKey, new ConcurrentHashMap<Integer, ChunkInfo>(8, 0.9f, 1));
		
		ConcurrentHashMap<Integer, ChunkInfo> chunksInfo = this.chunks.get(hashKey);
		
		int repDeg = Integer.parseInt(state.getFields()[Peer.repDegI]);
		if(chunksInfo.putIfAbsent(chunkKey, new ChunkInfo(hashKey + "." + chunkKey, repDeg, size)) != null) {
			chunksInfo.get(chunkKey).setDesiredRepDeg(repDeg);
			chunksInfo.get(chunkKey).setSize(size);
	    	SystemManager.getInstance().logPrint("updated chunk \"" + hashKey + "." + chunkKey + "\" with new desired repDeg " + repDeg + " and " + size + "KB", SystemManager.LogLevel.DATABASE);
	    	return;
		}
    	SystemManager.getInstance().logPrint("new chunk \"" + hashKey + "." + chunkKey + "\" with desired repDeg " + repDeg + " and " + size + "KB", SystemManager.LogLevel.DATABASE);
	}
	
	/**
	 * Updates the database with the received STORED message info about a chunk.
	 * Adds the sender ID to the perceived replication degree hash map.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	public void storedUpdate(ProtocolState state) {
		
		String hashKey = state.getFields()[Peer.hashI];
		int chunkKey = Integer.parseInt(state.getFields()[Peer.chunkNoI]);
		
		this.chunks.putIfAbsent(hashKey, new ConcurrentHashMap<Integer, ChunkInfo>(8, 0.9f, 1));
		
		ConcurrentHashMap<Integer, ChunkInfo> chunksInfo = this.chunks.get(hashKey);
		
		if(chunksInfo.putIfAbsent(chunkKey, new ChunkInfo(hashKey + "." + chunkKey)) != null) {
			chunksInfo.get(chunkKey).getPerceivedRepDeg().put(Integer.parseInt(state.getFields()[Peer.senderI]), 0);
	    	SystemManager.getInstance().logPrint("updated chunk \"" + hashKey + "." + chunkKey + "\" with new perceived repDeg " + chunksInfo.get(chunkKey).getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
	    	return;
		}
		
		chunksInfo.get(chunkKey).getPerceivedRepDeg().put(Integer.parseInt(state.getFields()[Peer.senderI]), 0);
    	SystemManager.getInstance().logPrint("new chunk \"" + hashKey + "." + chunkKey + "\" with perceived repDeg " + chunksInfo.get(chunkKey).getPerceivedRepDeg().size(), SystemManager.LogLevel.DATABASE);
	}
	
	/**
	 * Removes all database info of the SHA256 from the initiated
	 * backups database and from the chunks database.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 */
	public void deleteUpdate(ProtocolState state) {
		
		String hashKey = state.getFields()[Peer.hashI];

		this.initiatedFiles.remove(hashKey);
		this.chunks.remove(hashKey);
		
    	SystemManager.getInstance().logPrint("removed hash " + hashKey, SystemManager.LogLevel.DATABASE);
	}
	
	/**
	 * Updates the database with the initiated backup. Inserts new file info.
	 * If file info already exists updates the file path and desired replication degree.
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
	 * Updates the database with the REMOVED message info. Removes the sender ID
	 * from the perceived replication degree hash map.
	 * 
	 * @param state the Protocol State object relevant to this operation
	 * @return the desired replication degree for this chunk
	 */
	public int removedUpdate(ProtocolState state) {
		
		String hashKey = state.getFields()[Peer.hashI];
		int chunkKey = Integer.parseInt(state.getFields()[Peer.chunkNoI]);
		
		// Check that file hash exists
		if(!this.chunks.containsKey(hashKey)) {
	    	SystemManager.getInstance().logPrint("no data about " + hashKey, SystemManager.LogLevel.DATABASE);
			return -1;
		}

		ConcurrentHashMap<Integer, ChunkInfo> chunksInfo = this.chunks.get(hashKey);
		
		// Check that chunk exists
		if(!chunksInfo.containsKey(chunkKey)) {
	    	SystemManager.getInstance().logPrint("no data about " + hashKey + "." + chunkKey, SystemManager.LogLevel.DATABASE);
			return -1;
		}
		
		ChunkInfo chunkInfo = chunksInfo.get(chunkKey);

		// Update perceived replication degree
		chunkInfo.getPerceivedRepDeg().remove(Integer.parseInt(state.getFields()[Peer.senderI]));
		int size = chunkInfo.getPerceivedRepDeg().size();

	    SystemManager.getInstance().logPrint("updated chunk \"" + hashKey + "." + chunkKey + "\" with new perceived repDeg " + size, SystemManager.LogLevel.DATABASE);
	    
		if(chunkInfo.getSize() < 0) {
	    	SystemManager.getInstance().logPrint("no local copy of " + hashKey + "." + chunkKey, SystemManager.LogLevel.DEBUG);
			return -1;
		}
	    
	    if(size < chunkInfo.getDesiredRepDeg()) return chunkInfo.getDesiredRepDeg();
	    else {
	    	SystemManager.getInstance().logPrint("no backup needed for " + hashKey + "." + chunkKey, SystemManager.LogLevel.DEBUG);
	    	return -1;
	    }
	}
	
	/**
	 * @return the hash map of hash maps containing info about chunks in the system
	 */
	public ConcurrentHashMap<String, ConcurrentHashMap<Integer, ChunkInfo>> getChunks() {
		return chunks;
	}

	/**
	 * @param chunks the hash map of hash maps containing info about chunks in the system
	 */
	public void setChunks(ConcurrentHashMap<String, ConcurrentHashMap<Integer, ChunkInfo>> chunks) {
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

	@Override
	public void run() {
		
		Timer timer = new Timer();
		
		timer.scheduleAtFixedRate(new TimerTask() {
			  @Override
			  public void run() {
			    try {
					Peer.getInstance().getDatabase().saveDatabase();
				} catch (IOException e) {
					SystemManager.getInstance().logPrint("I/O Exception saving database periodically!", SystemManager.LogLevel.NORMAL);
					e.printStackTrace();
					return;
				}
			  }
			}, SystemDatabase.backupDelay, SystemDatabase.backupDelay);
	}
}
