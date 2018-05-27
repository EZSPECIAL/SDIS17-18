import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkInfo implements Serializable {
	
	private static final long serialVersionUID = -8533030704600707408L;
	
	private String id;
	private int desiredRepDeg;
	private ConcurrentHashMap<Integer, Integer> perceivedRepDeg = new ConcurrentHashMap<Integer, Integer>(8, 0.9f, 1);
	private int size = -1;

	/**
	 * Creates chunk info using specified parameters.
	 * 
	 * @param id the SHA256.chunkNo identifier
	 */
	public ChunkInfo(String id) {
		this.id = id;
		this.desiredRepDeg = 0;
	}
	
	/**
	 * Creates chunk info using specified parameters.
	 * 
	 * @param id the SHA256.chunkNo identifier
	 * @param desiredRepDeg the desired replication degree
	 */
	public ChunkInfo(String id, int desiredRepDeg) {
		this.id = id;
		this.desiredRepDeg = desiredRepDeg;
	}
	
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
	 * @param id the chunk ID to set
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
	 * @return the hash map containing the Peer IDs that have stored the chunk
	 */
	public  ConcurrentHashMap<Integer, Integer> getPerceivedRepDeg() {
		return perceivedRepDeg;
	}

	/**
	 * @param perceivedRepDeg the hash map containing the Peer IDs that have stored the chunk
	 */
	public void setPerceivedRepDeg( ConcurrentHashMap<Integer, Integer> perceivedRepDeg) {
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
