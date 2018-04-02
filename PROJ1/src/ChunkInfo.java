
public class ChunkInfo {
	
	private String id;
	private int desiredRepDeg;
	private int perceivedRepDeg = 0;
	private int size = -1;

	/**
	 * Creates chunk info using specified parameters.
	 * 
	 * @param id the SHA256.chunkNo identifier
	 * @param desiredRepDeg the desired replication degree
	 * @param size the size of the chunk in KB
	 */
	public ChunkInfo(String id, int desiredRepDeg, int size) {
		this.id = id;
		this.desiredRepDeg = desiredRepDeg;
		this.size = size;
	}
	
	/**
	 * @return the chunk ID
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param the chunk ID to set
	 */
	public void setId(String id) {
		this.id = id;
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
	 * @return the perceived replication degree
	 */
	public int getPerceivedRepDeg() {
		return perceivedRepDeg;
	}

	/**
	 * @param perceivedRepDeg the perceived replication degree to set
	 */
	public void setPerceivedRepDeg(int perceivedRepDeg) {
		this.perceivedRepDeg = perceivedRepDeg;
	}

	/**
	 * @return the chunk size in KB
	 */
	public int getSize() {
		return size;
	}

	/**
	 * @param size the chunk size in KB
	 */
	public void setSize(int size) {
		this.size = size;
	}
	
	
}
