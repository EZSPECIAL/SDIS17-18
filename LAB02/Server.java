import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class Server {

	/**
	 * Runs a server that broadcasts its service IP:Port so that a client
	 * can request operations on a license plate database.
	 * <br>
	 * Usage: java Server &lt;srvc_port&gt; &lt;mcast_addr&gt; &lt;mcast_port&gt;
	 *
	 * @param args 1. port number where server provides the service
	 * @param args 2. IP address of the multicast group used by the server
	 * @param args 3. port number of the multicast group used by the server
	 */
	public static void main(String[] args) throws IOException {

		// Parse command line broadcast address
		InetAddress bAddr = InetAddress.getByName(args[1]); // TODO remove magic number, validate input
		System.out.println(bAddr);

		// Create UDP socket and build broadcast message
		DatagramSocket socket = new DatagramSocket();

		InetAddress host = InetAddress.getLocalHost();
		String broadcast = host.toString() + ":" + args[0]; // TODO remove magic number, validate input

		System.out.println(broadcast);

		// Create packet and send it
		DatagramPacket broadcastPacket = new DatagramPacket(broadcast.getBytes(), broadcast.getBytes().length, bAddr, Integer.parseInt(args[2]));

		Timer multicast = new Timer();

		multicast.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {

				try {
					socket.send(broadcastPacket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, 0, 1000);

		while(true);
		//socket.close();
	}
}