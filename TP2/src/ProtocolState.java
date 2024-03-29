import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.DatatypeConverter;

public class ProtocolState {
	
	public enum ProtocolType {NONE, BACKUP, RESTORE, DELETE, RECLAIM, CHUNK_STOP, RETRIEVE}
	
	private static final int chunkSize = 64000;
	private static final int maxChunkTotal = 1000001;
	
	private ProtocolType protocolType;
	private ServiceMessage parser;
	
	// Last message fields and packet
	String[] fields;
	DatagramPacket packet;
	
	// Fields used for building ServiceMessage instances for sending
	private long chunkTotal;
	private long currentChunkNo;
	private int desiredRepDeg;
	private String protocolVersion;
	private String filepath;
	private String filename;
	private String hashHex;
	
	// Fields used for protocol logic
	private boolean isChunkMsgAlreadySent = false;
	private boolean isPutchunkMsgAlreadySent = false;
	private boolean isRetrieveMsgResponded = false;
	private FileInfo fileInfo;
	private ConcurrentHashMap<Long, HashSet<Integer>> respondedID = new ConcurrentHashMap<Long, HashSet<Integer>>(8, 0.9f, 1);
	private ConcurrentHashMap<Long, byte[]> restoredChunks = new ConcurrentHashMap<Long, byte[]>(8, 0.9f, 1);
	
	private boolean isFinished;

	/**
	 * Constructs a ProtocolState object used only for its parsing and storage capabilities and not for tracking
	 * the progress of a given ProtocolType.
	 * 
	 * @param parser the service message responsible for handling message for this protocol state
	 */
	public ProtocolState(ServiceMessage parser) {
		this.parser = parser;
		this.protocolType = ProtocolType.NONE;
		this.isFinished = true;
	}
	
	/**
	 * A Protocol State object maintains the state for a specific protocol running on the backup system.
	 * It keeps the relevant info needed for each protocol and is initiated by calling specific functions
	 * that handle each protocol type.
	 * 
	 * @param protocolType the protocol type
	 * @param parser the service message responsible for handling message for this protocol state
	 */
	public ProtocolState(ProtocolType protocolType, ServiceMessage parser) {
		this.protocolType = protocolType;
		this.parser = parser;
		this.isFinished = false;
	}

	/**
	 * Initialises the protocol state object for a backup procedure, calculates the total number of chunks of the file and the SHA256 hash
	 * of the filename and metadata. Returns whether initialisation was successful.
	 * 
	 * @param protocolVersion the backup system version
	 * @param filepath file path of the file to backup
	 * @param desiredRepDeg desired replication degree
	 * @return whether initialisation was successful.
	 */
	public boolean initBackupState(String protocolVersion, String filepath, int desiredRepDeg) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.filepath = filepath;
		
		// Validate total number of chunks
		this.chunkTotal = this.getTotalChunks(filepath);
		if(this.chunkTotal > maxChunkTotal) return false;
		
		this.currentChunkNo = 0;
		this.desiredRepDeg = desiredRepDeg;
		
		// Compute SHA256 of given filename
		File file = new File(filepath);
		this.filename = file.getName();
		this.hashHex = computeSHA256(filepath);
		
		// Fill hash map of peer responses
		for(long i = 0; i < this.chunkTotal; i++) {
			this.respondedID.put(i, new HashSet<Integer>());
		}
		
		return true;
	}

	/**
	 * Initialises the protocol state object for a backup response procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 * @param chunkNo the chunk number relevant to this response procedure
	 */
	public void initBackupResponseState(String protocolVersion, String hash, String chunkNo) {
		
		this.protocolVersion = protocolVersion;
		this.hashHex = hash;
		this.currentChunkNo = Long.parseLong(chunkNo);
	}

	/**
	 * Initialises the protocol state object for a delete procedure, computes the SHA256 hash
	 * of the filename and metadata.
	 * 
	 * @param protocolVersion the backup system version
	 * @param fileInfo the database object containing file info
	 */
	public void initDeleteState(String protocolVersion, FileInfo fileInfo) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.filepath = fileInfo.getFilepath();
		this.filename = fileInfo.getFilename();
		this.hashHex = fileInfo.getFileID();
		
		// Create hash set of deletion confirmations
		this.respondedID.put(0L, new HashSet<Integer>());
	}
	
	/**
	 * Initialises the protocol state object for a pending DELETE procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 */
	public void initPendingDeleteState(String protocolVersion, String hash) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.hashHex = hash;
				
		// Create hash set of deletion confirmations
		this.respondedID.put(0L, new HashSet<Integer>());
	}
	
	/**
	 * Initialises the protocol state object for a delete response procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 */
	public void initDeleteResponseState(String protocolVersion, String hash) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.hashHex = hash;
	}
	
	/**
	 * Initialises the protocol state object for a restore procedure, calculates the total number of chunks of the file and the SHA256 hash
	 * of the filename and metadata. Returns whether initialisation was successful.
	 * 
	 * @param protocolVersion the backup system version
	 * @param fileInfo the database object containing file info
	 */
	public void initRestoreState(String protocolVersion, FileInfo fileInfo) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.filepath = fileInfo.getFilepath();
		this.chunkTotal = fileInfo.getTotalChunks();
		this.currentChunkNo = 0;
		this.filename = fileInfo.getFilename();
		this.hashHex = fileInfo.getFileID();
	}
	
	/**
	 * Initialises the protocol state object for a restore response procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 * @param filepath file path of the file to backup
	 * @param chunkNo the chunk number relevant to this response procedure
	 */
	public void initRestoreResponseState(String protocolVersion, String hash, String filepath, String chunkNo) {
		
		this.protocolVersion = protocolVersion;
		this.filepath = filepath;
		this.hashHex = hash;
		this.currentChunkNo = Long.parseLong(chunkNo);
	}
	
	/**
	 * Initialises the protocol state object for a removed procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 * @param chunkNo the chunk number relevant to this response procedure
	 */
	public void initRemovedState(String protocolVersion, String hash, String chunkNo) {
		
		this.protocolVersion = protocolVersion;
		this.hashHex = hash;
		this.currentChunkNo = Long.parseLong(chunkNo);
	}
	
	/**
	 * Initialises the protocol state object for a reclaim procedure by setting the required fields.
	 * 
	 * @param protocolVersion the backup system version
	 * @param hash textual representation of the hexadecimal values of a SHA256
	 * @param chunkNo the chunk number relevant to this response procedure
	 * @param filepath the file path
	 * @param desiredRepDeg desired replication degree
	 */
	public void initReclaimState(String protocolVersion, String hash, String chunkNo, String filepath, int desiredRepDeg) {
		
		this.protocolVersion = protocolVersion;
		this.hashHex = hash;
		this.currentChunkNo = Long.parseLong(chunkNo);
		this.filepath = filepath;
		this.desiredRepDeg = desiredRepDeg;
	}
	
	/**
	 * Calculates the total chunks of {@value #chunkSize} bytes needed to backup the file specified.
	 * 
	 * @param filepath the file path to calculate total chunks from
	 * @return the numeric value of the total chunks
	 */
	public long getTotalChunks(String filepath) throws IOException {

		Path path = Paths.get(filepath);
		Files.size(path);
		long result = Files.size(path) / chunkSize + 1;
		
		String totalChunks = "total chunks: " + result + " - file multiple of 64KB: " + (Files.size(path) % chunkSize == 0);
		SystemManager.getInstance().logPrint(totalChunks, SystemManager.LogLevel.VERBOSE);
		
		return result;
	}

	/**
	 * Computes a SHA256 hash based on the specified file path. The function uses the file's
	 * filename and last modified date to add more uniqueness to the hash.
	 * 
	 * @param filepath the file path to use as base for the hash
	 * @return textual representation of the hexadecimal values of the SHA256
	 */
	private String computeSHA256(String filepath) throws IOException, NoSuchAlgorithmException {
		
		File file = new File(filepath);
		Path attrRead = Paths.get(filepath);

		// Get file metadata for hashing
	    String modify = Files.getAttribute(attrRead, "lastModifiedTime").toString();
		String toHash = file.getName() + modify;
		
        String beforeHash = "string to hash: " + toHash;
        SystemManager.getInstance().logPrint(beforeHash, SystemManager.LogLevel.VERBOSE);
        
        // Hash the string with SHA256
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
		
		String hashHex = DatatypeConverter.printHexBinary(hash);
		
        String afterHash = "hash: " + hashHex;
        SystemManager.getInstance().logPrint(afterHash, SystemManager.LogLevel.VERBOSE);
        
        return hashHex;
	}
	
	/**
	 * Increments current chunk number being processed by 1 and sets isFinished field if last chunk
	 */
	public void incrementCurrentChunkNo() {
		this.currentChunkNo++;
		if(this.chunkTotal == this.currentChunkNo) this.isFinished = true;
	}

	/**
	 * @return the protocol type enumerator
	 */
	public ProtocolType getProtocolType() {
		return protocolType;
	}

	/**
	 * @return the ServiceMessage instance responsible for creating and parsing service messages
	 */
	public ServiceMessage getParser() {
		return parser;
	}

	/**
	 * @return service message header fields
	 */
	public String[] getFields() {
		return fields;
	}

	/**
	 * @return the packet
	 */
	public DatagramPacket getPacket() {
		return packet;
	}

	/**
	 * @return the total number of chunks
	 */
	public long getChunkTotal() {
		return chunkTotal;
	}

	/**
	 * @return the current chunk number being processed
	 */
	public long getCurrentChunkNo() {
		return currentChunkNo;
	}

	/**
	 * @return the desired replication degree
	 */
	public int getDesiredRepDeg() {
		return desiredRepDeg;
	}
	
	/**
	 * @return the protocol version, "1.0" for base version or "1.1" for enhanced version
	 */
	public String getProtocolVersion() {
		return protocolVersion;
	}

	/**
	 * @return the file path relevant to this protocol
	 */
	public String getFilepath() {
		return filepath;
	}

	/**
	 * @return the filename relevant to this protocol
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @return the SHA256 hash of the filename + last modified + last accessed
	 */
	public String getHashHex() {
		return hashHex;
	}

	/**
	 * @return the chunksize
	 */
	public static int getChunksize() {
		return chunkSize;
	}

	/**
	 * @return the maxchunktotal
	 */
	public static int getMaxchunktotal() {
		return maxChunkTotal;
	}

	/**
	 * @return the hash map of unique Peer IDs that have responded to a certain chunkNo message
	 */
	public ConcurrentHashMap<Long, HashSet<Integer>> getRespondedID() {
		return respondedID;
	}

	/**
	 * @param fields the service message header fields to set
	 */
	public void setFields(String[] fields) {
		this.fields = fields;
	}
	
	/**
	 * @param packet the packet to set
	 */
	public void setPacket(DatagramPacket packet) {
		this.packet = packet;
	}

	/**
	 * @return whether a CHUNK message was found for the same CHUNK message that would've been sent
	 */
	public boolean isChunkMsgAlreadySent() {
		return isChunkMsgAlreadySent;
	}

	/**
	 * @return whether a PUTCHUNK message was found for the same PUTCHUNK message that would've been sent
	 */
	public boolean isPutchunkMsgAlreadySent() {
		return isPutchunkMsgAlreadySent;
	}

	/**
	 * @return the hash map of currently stored chunks
	 */
	public ConcurrentHashMap<Long, byte[]> getRestoredChunks() {
		return restoredChunks;
	}

	/**
	 * @return whether the protocol instance has terminated
	 */
	public boolean isFinished() {
		return isFinished;
	}
	
	/**
	 * @param isChunkMsgAlreadySent whether a CHUNK message was found for the same CHUNK message that would've been sent
	 */
	public void setChunkMsgAlreadySent(boolean isChunkMsgAlreadySent) {
		this.isChunkMsgAlreadySent = isChunkMsgAlreadySent;
	}
	
	/**
	 * @param isPutchunkMsgAlreadySent whether a PUTCHUNK message was found for the same PUTCHUNK message that would've been sent
	 */
	public void setPutchunkMsgAlreadySent(boolean isPutchunkMsgAlreadySent) {
		this.isPutchunkMsgAlreadySent = isPutchunkMsgAlreadySent;
	}
	
	/**
	 * @param restoredChunks the hash map of currently stored chunks
	 */
	public void setRestoredChunks(ConcurrentHashMap<Long, byte[]> restoredChunks) {
		this.restoredChunks = restoredChunks;
	}
	
	/**
	 * @param isFinished whether the protocol instance has terminated
	 */
	public void setFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}

	/**
	 * @return whether the response to a RETRIEVE message has been recorded
	 */
	public boolean isRetrieveMsgResponded() {
		return isRetrieveMsgResponded;
	}

	/**
	 * @param isRetrieveMsgResponded whether the response to a RETRIEVE message has been recorded
	 */
	public void setRetrieveMsgResponded(boolean isRetrieveMsgResponded) {
		this.isRetrieveMsgResponded = isRetrieveMsgResponded;
	}

	/**
	 * @return the file info object containing info about a backed up file
	 */
	public FileInfo getFileInfo() {
		return fileInfo;
	}

	/**
	 * @param fileInfo the file info object containing info about a backed up file to set
	 */
	public void setFileInfo(FileInfo fileInfo) {
		this.fileInfo = fileInfo;
	}
}
