import java.io.IOException;
import java.net.*;

public class Client {

	// TODO improve javadoc
	/**
	 * Receives data to register or lookup a license plate.
	 * 
	 * Usage: java Client &lt;mcast_addr&gt; &lt;mcast_port&gt; &lt;oper&gt; &lt;opnd&gt;&#42
	 * 
	 * @param args 1. IP address of the multicast group used by the server
	 * @param args 2. port number where server provides the service
	 * @param args 3.
	 * @param args 4.
	 */
	public static void main(String[] args) throws IOException, UnknownHostException {
		
		// Create multicast socket and join group
		InetAddress group = InetAddress.getByName(args[0]); // TODO validate input, remove magic number
		MulticastSocket multicast = new MulticastSocket(Integer.parseInt(args[1])); // TODO validate input, remove magic number
		
		multicast.joinGroup(group);
		
		// Create datagram packet to receive multicast message
		byte[] data = new byte[512];
		DatagramPacket dataPacket = new DatagramPacket(data, data.length);
		
		multicast.receive(dataPacket);
		
		multicast.leaveGroup(group);
		multicast.close();
		
		// Convert packet to string and clean garbage characters
		String msg = new String(dataPacket.getData());
		msg = msg.trim();
		System.out.println(msg);
		
		// TODO validate split results
		String[] interRes = msg.split("/");
		String[] result = interRes[1].split(":");
		
		InetAddress serviceAddr = InetAddress.getByName(result[0]);
		Integer servicePort = Integer.parseInt(result[1]);
		
		System.out.println(serviceAddr);
		System.out.println(servicePort);

		// Send operation to server
		String operation = "hello"; // TODO insert actual operations from LAB01
		
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket operationPacket = new DatagramPacket(operation.getBytes(), operation.getBytes().length, serviceAddr, servicePort);
		
		socket.send(operationPacket);
		socket.close();
	}
}