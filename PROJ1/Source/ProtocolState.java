import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;

import javax.xml.bind.DatatypeConverter;

public class ProtocolState {
	
	public enum ProtocolType {INIT_BACKUP, BACKUP, INIT_RESTORE, RESTORE, INIT_DELETE, DELETE, INIT_RECLAIM, RECLAIM}
	
	private static final int chunkSize = 64000;
	private static final int maxChunkTotal = 1000001;
	
	private ProtocolType protocolType;
	private long chunkTotal;
	private long currentChunkNo;
	private int desiredRepDeg;
	private String protocolVersion;
	private String filepath;
	private String filename;
	private String hashHex;
	
	private int attempts;
	private HashSet<Integer> respondedID = new HashSet<Integer>();
	private boolean isStoredCountCorrect = false;
	
	private boolean isFinished;

	/**
	 * Default constructor used for populating Protocol State fields to avoid checking for null.
	 */
	public ProtocolState() {
		this.isFinished = true;
	}
	
	/**
	 * A Protocol State object maintains the state for a specific protocol running on the backup system.
	 * It keeps the relevant info needed for each protocol and is initiated by calling specific functions
	 * that handle each protocol type.
	 * 
	 * @param protocolType the protocol type
	 */
	public ProtocolState(ProtocolType protocolType) {
		this.protocolType = protocolType;
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
		
		this.attempts = 0;
		
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
	 * Calculates the total chunks of {@value #chunkSize} bytes needed to backup the file specified.
	 * 
	 * @param filepath the file path to calculate total chunks from
	 * @return the numeric value of the total chunks
	 */
	private long getTotalChunks(String filepath) throws IOException {

		Path path = Paths.get(filepath);
		Files.size(path);
		long result = Files.size(path) / chunkSize + 1;
		
		String totalChunks = "total chunks: " + result + " - file multiple of 64KB: " + (Files.size(path) % chunkSize == 0);
		SystemManager.getInstance().logPrint(totalChunks, SystemManager.LogLevel.VERBOSE);
		
		return result;
	}

	/**
	 * Computes a SHA256 hash based on the specified file path. The function uses the file's
	 * filename, last modified date and last accessed date to add more uniqueness to the hash.
	 * 
	 * @param filepath the file path to use as base for the hash
	 * @return textual representation of the hexadecimal values of the SHA256
	 */
	private String computeSHA256(String filepath) throws IOException, NoSuchAlgorithmException {
		
		File file = new File(filepath);
		Path attrRead = Paths.get(filepath);

		// Get file's last modified and access times to build a string for hashing
	    String modify = Files.getAttribute(attrRead, "lastModifiedTime").toString();
	    String access = Files.getAttribute(attrRead, "lastAccessTime").toString();
		String toHash = file.getName() + modify + access;
		
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
	 * Resets the hash set of unique Peer IDs that have responded to a message and its flag.
	 */
	public void resetStoredCount() {
		
		this.respondedID = new HashSet<Integer>();
		this.isStoredCountCorrect = false;
	}

	/**
	 * Increments current chunk number being processed by 1 and sets isFinished field if last chunk
	 */
	public void incrementCurrentChunkNo() {
		this.currentChunkNo++;
		if(this.chunkTotal == this.currentChunkNo) this.isFinished = true;
	}
	
	/**
	 * @param attempts increments the number of attempts for this protocol instance
	 */
	public void incrementAttempts() {
		this.attempts++;
	}
	
	/**
	 * @return the protocol type enumerator
	 */
	public ProtocolType getProtocolType() {
		return protocolType;
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
	 * @return the number of attempts for this protocol instance
	 */
	public int getAttempts() {
		return attempts;
	}
	
	/**
	 * @return the hash set of unique Peer IDs that have responded to a message
	 */
	public HashSet<Integer> getRespondedID() {
		return respondedID;
	}

	/**
	 * @return whether the number of STORED responses is enough for the desired replication degree
	 */
	public boolean isStoredCountCorrect() {
		return isStoredCountCorrect;
	}

	/**
	 * @return whether the protocol instance has terminated
	 */
	public boolean isFinished() {
		return isFinished;
	}
	
	/**
	 * @param whether the number of STORED responses is enough for the desired replication degree
	 */
	public void setStoredCountCorrect(boolean isStoredCountCorrect) {
		this.isStoredCountCorrect = isStoredCountCorrect;
	}
	
	/**
	 * @param isFinished whether the protocol instance has terminated
	 */
	public void setFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}
}
