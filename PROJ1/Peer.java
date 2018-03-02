import java.io.IOException;
import java.util.TimerTask;
import java.net.*;
import java.util.Timer;


public class Peer {

	/**
	 * Runs a serverles backup service.
	 * <br><br>
	 * Usage: java Peer &lt;protocol version&gt; &lt;peer id&gt; &lt;service access point&gt; &lt;mcc_ip&gt; &lt;mcc_port&gt; &lt;mdb_ip&gt; &lt;mdb_port&gt; &lt;mdr_ip&gt; &lt;mdr_port&gt;
	 * <br>
	 * Usage: java Peer &lt;protocol version&gt; &lt;peer id&gt; &lt;service access point&gt; &lt;mcc_ip&gt; &lt;mcc_port&gt; &lt;mdb_ip&gt; &lt;mdb_port&gt; &lt;mdr_ip&gt; &lt;mdr_port&gt; &lt;filename&gt; &lt;repl degree&gt;
	 *
	 * @param args 1.  protocol version
	 * @param args 2.  peer ID
	 * @param args 3.  service access point
	 * @param args 4.  multicast control channel IP
	 * @param args 5.  multicast control channel port
	 * @param args 6.  multicast data backup IP
	 * @param args 7.  multicast data backup port
	 * @param args 8.  multicast data recovery IP
	 * @param args 9.  multicast data recovery port
	 * @param args 10. filename to backup
	 * @param args 11. replication degree
	 */
	public static void main(String[] args) throws IOException {

		// Parse argument number
		if(args.length == 11) {
			System.out.println("Initiator!");
		} else if(args.length == 9) {
			
			System.out.println("Peer!");
		} else {
			System.out.println("Wrong argument number!");
			System.exit(1);
		}
		
		// Parse command line multicast IPs
		InetAddress mccAddr = InetAddress.getByName(args[3]); // TODO remove magic number, validate input
		InetAddress mdbAddr = InetAddress.getByName(args[5]); // TODO remove magic number, validate input
		InetAddress mdrAddr = InetAddress.getByName(args[7]); // TODO remove magic number, validate input
		
		// Parse command line multicast ports
		int mccPort = Integer.parseInt(args[4]);
		int mdbPort = Integer.parseInt(args[6]);
		int mdrPort = Integer.parseInt(args[8]);

//		// Create UDP socket and build broadcast message
//		DatagramSocket socket = new DatagramSocket();
//
//		InetAddress host = InetAddress.getLocalHost();
//		String broadcast = host.toString() + ":" + args[0]; // TODO remove magic number, validate input
//
//		System.out.println(broadcast);
//
//		// Create packet and send it
//		DatagramPacket broadcastPacket = new DatagramPacket(broadcast.getBytes(), broadcast.getBytes().length, bAddr, Integer.parseInt(args[2]));

		String broadcast = "PUTCHUNK";
		
		DatagramSocket socket = new DatagramSocket();
		DatagramPacket broadcastPacket = new DatagramPacket(broadcast.getBytes(), broadcast.getBytes().length, mdbAddr, mdbPort);
		
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

//		// Await database operations from clients
//		byte[] data = new byte[512];
//		DatagramSocket service = new DatagramSocket(Integer.parseInt(args[0])); // TODO remove magic number, validate input
//		DatagramPacket dataPacket = new DatagramPacket(data, data.length);
//
//		System.out.println("Receiving...");
//		service.receive(dataPacket);
//
//		// Convert packet to string and clean garbage characters
//		String msg = new String(dataPacket.getData());
//		msg = msg.trim();
//		System.out.println(msg);
//
//		// Cancel Timer thread and close UDP sockets
//		multicast.cancel();
//
//		service.close();
//		socket.close();
	}
}
