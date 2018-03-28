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
	
	// TODO document
	public ServiceChannel(InetAddress addr, int port, String channelName) {
		
		this.addr = addr;
		this.port = port;
		this.channelName = channelName;
		
		try {
			this.socket = new MulticastSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// TODO document
	public DatagramPacket listen() {
		
		byte[] binData = new byte[packetSize];
		DatagramPacket packet = new DatagramPacket(binData, binData.length);
		
        String msg = "receiving packets on " + this.channelName;
        SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);
		
		try {
			socket.joinGroup(this.addr);
			socket.receive(packet);
			socket.leaveGroup(this.addr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return packet;
	}
	
	// TODO document
	public void send(byte[] data) {
		
		DatagramPacket packet = new DatagramPacket(data, data.length, this.addr, this.port);
		
        String sent = "sending packets on " + this.channelName;
        SystemManager.getInstance().logPrint(sent, SystemManager.LogLevel.DEBUG);
        String dataContent = "data [" + data + "]";
        SystemManager.getInstance().logPrint(dataContent, SystemManager.LogLevel.VERBOSE);
		
		try {
			this.socket.send(packet);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}