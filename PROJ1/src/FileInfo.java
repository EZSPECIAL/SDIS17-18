
public class FileInfo {

	private String filepath;
	private String fileID;
	private int desiredRepDeg;
	
	/**
	 * Creates file info using specified parameters.
	 * 
	 * @param filepath the file path of the backed up file
	 * @param fileID textual representation of the hexadecimal values of the SHA256 of the backed up file
	 */
	public FileInfo(String filepath, String fileID, int desiredRepDeg) {
		this.filepath = filepath;
		this.fileID = fileID;
		this.desiredRepDeg = desiredRepDeg;
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
	
}
