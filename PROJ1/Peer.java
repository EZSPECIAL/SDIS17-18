import java.io.IOException;
import java.util.TimerTask;
import java.net.*;
import java.util.Timer;


public class Peer {

	private static InetAddress mccAddr;
	private static InetAddress mdbAddr;
	private static InetAddress mdrAddr;

	private static int mccPort;
	private static int mdbPort;
	private static int mdrPort;

	/**
	 * Runs a serverless backup service.
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

		// Parse command line multicast IPs
		InetAddress mccAddr = InetAddress.getByName(args[3]); // TODO remove magic number, validate input
		InetAddress mdbAddr = InetAddress.getByName(args[5]); // TODO remove magic number, validate input
		InetAddress mdrAddr = InetAddress.getByName(args[7]); // TODO remove magic number, validate input

		// Parse command line multicast ports
		int mccPort = Integer.parseInt(args[4]);
		int mdbPort = Integer.parseInt(args[6]);
		int mdrPort = Integer.parseInt(args[8]);

		// Check what type of peer was called
		if(args.length == 11) {
			System.out.println("BACK: Initiator peer started."); // TODO add more info
			initPeer();
		} else if(args.length == 9) {
			System.out.println("BACK: Peer started."); // TODO add more info
		} else {
			System.out.println("Wrong number of arguments!");
			System.exit(1);
		}

		//
		//		// Cancel Timer thread and close UDP sockets
		//		multicast.cancel();
		//
		//		service.close();
		//		socket.close();
	}

	private static void initPeer() throws SocketException {

		// Initialize socket and broadcast message
		DatagramSocket socket = new DatagramSocket();
		String broadcast = "PUTCHUNK";
		DatagramPacket broadcastPacket = new DatagramPacket(broadcast.getBytes(), broadcast.getBytes().length, mdbAddr, mdbPort);

		// Repeat message every second
		Timer repeatMsg = new Timer();

		repeatMsg.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {

				try {

					System.out.println("INIT: sent \"" + broadcast +"\"");
					socket.send(broadcastPacket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, 0, 1000);
	}

	private static void peer() throws IOException {

		byte[] data = new byte[64000];
		DatagramPacket dataPacket = new DatagramPacket(data, data.length, mdbAddr, mdbPort);

		MulticastSocket multi = new MulticastSocket(mdbPort);
		multi.joinGroup(mdbAddr);

		System.out.println("Waiting");
		multi.receive(dataPacket);

		String msg = new String(dataPacket.getData());
		msg = msg.trim();
		System.out.println(msg);
	}
}