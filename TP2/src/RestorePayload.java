import java.io.Serializable;
import java.net.DatagramPacket;

public class RestorePayload implements Serializable {

	private static final long serialVersionUID = -7788779364023178049L;
	
	private byte[] data;
	private int length;

	/**
	 * Serializable wrapper for DatagramPacket. Stores data and length.
	 * 
	 * @param payload the byte array containing the data
	 */
	public RestorePayload(byte[] payload) {
		
		DatagramPacket packet = new DatagramPacket(payload, payload.length);
		this.data = packet.getData();
		this.length = packet.getLength();
	}

	/**
	 * @return the data associated with this payload
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * @return the length of this payload
	 */
	public int getLength() {
		return length;
	}
}
