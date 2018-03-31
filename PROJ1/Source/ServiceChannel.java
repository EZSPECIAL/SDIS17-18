import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

public class ServiceChannel {

	// Multicast socket settings
	private InetAddress addr;
	private int port;
	private String channelName;
	private MulticastSocket socket;
	
	private static final int packetSize = 65000;
	
	/**
	 * A Service Channel is an object that provides methods for receiving and sending UDP packets
	 * over a multicast socket created on invocation of its constructor.
	 * 
	 * @param addr address of the channel
	 * @param port port for the channel
	 * @param channelName the channel name
	 */
	public ServiceChannel(InetAddress addr, int port, String channelName) {
		
		this.addr = addr;
		this.port = port;
		this.channelName = channelName;
		
		try {
			this.socket = new MulticastSocket(port);
		} catch (IOException e) {
			
	        String msg = "failed to open socket with name \"" + channelName + "\"";
	        SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * Wait for a packet on the UDP socket and returns it.
	 * 
	 * @param timeout the timeout to receive in milliseconds
	 * @return the packet received
	 */
	public DatagramPacket listen(int timeout) throws IOException {
		
		byte[] binData = new byte[packetSize];
		DatagramPacket packet = new DatagramPacket(binData, binData.length);
		
        String msg = "receiving packets on \"" + this.channelName + "\" with " + timeout + "ms timeout";
        SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);

        socket.joinGroup(this.addr);
		socket.setSoTimeout(timeout); // LATER use timeout on thread handler

        try {
			socket.receive(packet);
		} catch(SocketTimeoutException e) {
	        String err = "timed out: " + this.channelName;
	        SystemManager.getInstance().logPrint(err, SystemManager.LogLevel.DEBUG);
	        socket.leaveGroup(this.addr);
	        return null;
		}
        
        socket.leaveGroup(this.addr);
		return packet;
	}

	/**
	 * Sends a packet on the UDP socket.
	 * 
	 * @param data the data to send
	 */
	public void send(byte[] data) throws IOException {
		
		DatagramPacket packet = new DatagramPacket(data, data.length, this.addr, this.port);
		
        String sent = "sending packets on " + this.channelName;
        SystemManager.getInstance().logPrint(sent, SystemManager.LogLevel.DEBUG);

		this.socket.send(packet);
	}

}