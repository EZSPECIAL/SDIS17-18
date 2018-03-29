import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ServiceChannel {

	// Multicast socket settings
	private InetAddress addr;
	private int port;
	private String channelName;
	private MulticastSocket socket;
	
	private static final int packetSize = 65000;
	
	// DOC document
	public ServiceChannel(InetAddress addr, int port, String channelName) {
		
		this.addr = addr;
		this.port = port;
		this.channelName = channelName;
		
		try {
			this.socket = new MulticastSocket(port);
		} catch (IOException e) {
			// CATCH Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// DOC document
	public DatagramPacket listen() throws IOException {
		
		byte[] binData = new byte[packetSize];
		DatagramPacket packet = new DatagramPacket(binData, binData.length);
		
        String msg = "receiving packets on " + this.channelName;
        SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);

        socket.joinGroup(this.addr);
        socket.receive(packet);
        socket.leaveGroup(this.addr);
		
		return packet;
	}
	
	// DOC document
	public void send(byte[] data) throws IOException {
		
		DatagramPacket packet = new DatagramPacket(data, data.length, this.addr, this.port);
		
        String sent = "sending packets on " + this.channelName;
        SystemManager.getInstance().logPrint(sent, SystemManager.LogLevel.DEBUG);

		this.socket.send(packet);
	}

}