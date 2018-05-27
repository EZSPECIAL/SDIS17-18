import java.io.File;
import java.io.Serializable;

public class FileInfo implements Serializable {

	private static final long serialVersionUID = -629018722917809320L;
	
	private String filepath;
	private String filename;
	private String fileID;
	private long totalChunks;
	private int desiredRepDeg;
	
	/**
	 * Creates file info using specified parameters.
	 * 
	 * @param filepath the file path of the backed up file
	 * @param fileID textual representation of the hexadecimal values of the SHA256 of the backed up file
	 * @param desiredRepDeg desired replication degree
	 */
	public FileInfo(String filepath, String fileID, long totalChunks, int desiredRepDeg) {
		
		this.filepath = filepath;
		this.fileID = fileID;
		this.desiredRepDeg = desiredRepDeg;
		this.totalChunks = totalChunks;
		
		File file = new File(this.filepath);
		this.filename = file.getName();
	}
	
	/**
	 * Creates file info using specified parameters.
	 * 
	 * @param filepath the file path of the backed up file
	 * @param filename the file name of the file
	 * @param fileID textual representation of the hexadecimal values of the SHA256 of the backed up file
	 * @param totalChunks the total number of chunks of the file
	 */
	public FileInfo(String filepath, String filename, String fileID, long totalChunks) {
		
		this.filepath = filepath;
		this.filename = filename;
		this.fileID = fileID;
		this.totalChunks = totalChunks;
	}

	/**
	 * @return the file path of the operation
	 */
	public String getFilepath() {
		return filepath;
	}

	/**
	 * @param filepath the file path of the operation to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}

	/**
	 * @return the SHA256 of this file
	 */
	public String getFileID() {
		return fileID;
	}

	/**
	 * @param fileID the SHA256 of this file to set
	 */
	public void setFileID(String fileID) {
		this.fileID = fileID;
	}

	/**
	 * @return the desired replication degree
	 */
	public int getDesiredRepDeg() {
		return desiredRepDeg;
	}

	/**
	 * @param desiredRepDeg the desired replication degree to set
	 */
	public void setDesiredRepDeg(int desiredRepDeg) {
		this.desiredRepDeg = desiredRepDeg;
	}

	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @return the total chunks for this file
	 */
	public long getTotalChunks() {
		return totalChunks;
	}

	/**
	 * @param totalChunks the total chunks to set for this file
	 */
	public void setTotalChunks(long totalChunks) {
		this.totalChunks = totalChunks;
	}
}
