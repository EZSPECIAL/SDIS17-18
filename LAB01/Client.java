import java.io.IOException;
import java.net.*;

public class Client {

	/**
	 * Receives data to register or lookup a license plate.
	 * 
	 * Format: <host_name> <port_number> <oper> <opnd>*
	 * 
	 * @param args client arguments for communicating with server
	 * @throws IOException, UnknownHostException 
	 */
	public static void main(String[] args) throws IOException, UnknownHostException {
		
		InetAddress addr = null;
		
		// Get host address from host name, prints message on unknown host
		try {
			addr = InetAddress.getByName(args[0]);
		} catch(UnknownHostException e) {
			Client.printError("host name not found!");
			System.exit(1);
		}
		
		// TODO Format debug info
		for(String arg : args) {
			System.out.print("arg: ");
			System.out.println(arg);
		}

		// Parse register operation
		if(args[2].compareTo("register") == 0) {
			
			String[] result = args[3].split(":");
			for(String values : result) {
				
				System.out.print("operands: ");
				System.out.println(values);
			}
		}
		
		String data = args[2] + " " + args[3];

		DatagramSocket socket = new DatagramSocket();
		DatagramPacket dataPacket = new DatagramPacket(data.getBytes(), data.length(), addr, Integer.parseInt(args[1]));
		
		socket.send(dataPacket);
		
		socket.close();
	}
	
	private static void printError(String message) {
		System.out.println("Client: " + message);
	}
}