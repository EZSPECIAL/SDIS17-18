import java.io.IOException;
import java.net.*;

public class Server {

	/**
	 * Runs local server that handles access requests to the license plate database
	 * 
	 * Format: <port_number>
	 * 
	 * @param args server port
	 */
	public static void main(String[] args) throws IOException {
		
		DatagramSocket socket = new DatagramSocket(Integer.parseInt(args[0]));
		DatagramPacket data = new DatagramPacket();
		
		while(true) {
			
			socket.receive();
		}
	}
}
