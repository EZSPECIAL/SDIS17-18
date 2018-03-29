import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ProtocolState {
	
	public enum ProtocolType {INIT_BACKUP, BACKUP, INIT_RESTORE, RESTORE, INIT_DELETE, DELETE, INIT_RECLAIM, RECLAIM}
	
	private static final int chunkSize = 64000;
	
	private ProtocolType protocolType;
	private int chunkTotal;
	private boolean isSizeMultiple;
	private int currentChunkNo;
	private int desiredRepDeg;
	private String protocolVersion;
	private String filepath;
	private String filename;
	private String hashHex;

	// DOC document
	public ProtocolState(ProtocolType protocolType) {
		this.protocolType = protocolType;
	}
	
	// DOC document
	public void initBackupState(String protocolVersion, String filepath, int desiredRepDeg) throws NoSuchAlgorithmException, IOException {
		
		this.protocolVersion = protocolVersion;
		this.filepath = filepath;
		this.chunkTotal = this.getTotalChunks(filepath);
		this.currentChunkNo = 0;
		this.desiredRepDeg = desiredRepDeg;
		
		File file = new File(filepath);
		this.filename = file.getName();
		
		this.hashHex = computeSHA256(filepath);
	}
	
	// DOC document
	private int getTotalChunks(String filepath) throws IOException {
		
		Path path = Paths.get(filepath);
		Files.size(path);

		// Calculate total 64KB chunks and check if size is multiple of 64KB
		int result = (int) Files.size(path) / chunkSize + ((Files.size(path) % chunkSize == 0) ? 0 : 1);
		this.isSizeMultiple = (Files.size(path) % chunkSize == 0) ? true : false;

		String totalChunks = "total chunks: " + result + " - file multiple of 64KB: " + this.isSizeMultiple;
		SystemManager.getInstance().logPrint(totalChunks, SystemManager.LogLevel.VERBOSE);
		
		return result;
	}
	
	// DOC document
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
		String hashHex = String.format("%040X", new BigInteger(1, hash));
		
        String afterHash = "hash: " + hashHex;
        SystemManager.getInstance().logPrint(afterHash, SystemManager.LogLevel.VERBOSE);
        
        return hashHex;
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
	public int getChunkTotal() {
		return chunkTotal;
	}

	/**
	 * @return whether file size is multiple of chunk size
	 */
	public boolean isSizeMultiple() {
		return isSizeMultiple;
	}

	/**
	 * @return the current chunk number being processed
	 */
	public int getCurrentChunkNo() {
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
}
