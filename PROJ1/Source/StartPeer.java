import java.io.File;
import java.io.IOException;
import java.util.TimerTask;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

public class StartPeer {

	private static InetAddress mccAddr;
	private static InetAddress mdbAddr;
	private static InetAddress mdrAddr;

	private static int mccPort;
	private static int mdbPort;
	private static int mdrPort;
	
	private static final String logFolder = "logFiles";
	private static final String logPrefix = "PeerLog_";

	private static StartPeer singleton = new StartPeer();

	private StartPeer() {}
	
	public static StartPeer getInstance( ) {
		return singleton;
	}
	
	public void printToLog(int PeerID, String message) {
		
		String filepath = "./" + logFolder + "/" + logPrefix + PeerID + ".txt";
		
		List<String> lines = Arrays.asList(message);
		Path file = Paths.get(filepath);
		
		File createFile = new File(filepath);
		
		try {
			createFile.createNewFile();
			Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	/**
	 * Runs a serverless backup service.
	 * <br><br>
	 * Usage: java Peer &lt;protocol version&gt; &lt;peer id&gt; &lt;service access point&gt; &lt;mcc_ip&gt; &lt;mcc_port&gt; &lt;mdb_ip&gt; &lt;mdb_port&gt; &lt;mdr_ip&gt; &lt;mdr_port&gt;
	 * <br>
	 * Usage: java Peer &lt;protocol version&gt; &lt;peer id&gt; &lt;service access point&gt; &lt;mcc_ip&gt; &lt;mcc_port&gt; &lt;mdb_ip&gt; &lt;mdb_port&gt; &lt;mdr_ip&gt; &lt;mdr_port&gt; &lt;filename&gt; &lt;repl degree&gt;
	 *
	 * @param args 1.  protocol version
	 * @param args 2.  peer ID
	 * @param args 3.  service access point (RMI Object name)
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

	    File directory = new File(logFolder);
	    if(!directory.exists()) {
	        directory.mkdir();
	    }
		
		System.setProperty("java.net.preferIPv4Stack", "true");
		
		// Parse command line multicast IPs
		mccAddr = InetAddress.getByName(args[3]); // TODO remove magic number, validate input
		mdbAddr = InetAddress.getByName(args[5]); // TODO remove magic number, validate input
		mdrAddr = InetAddress.getByName(args[7]); // TODO remove magic number, validate input

		// Parse command line multicast ports
		mccPort = Integer.parseInt(args[4]);
		mdbPort = Integer.parseInt(args[6]);
		mdrPort = Integer.parseInt(args[8]);
		
		initRMI(args[2]);
		
		// Check what type of peer was called
		if(args.length == 11) {
			System.out.println("BACK: Initiator peer started."); // TODO add more info
			initPeer();
		} else if(args.length == 9) {
			System.out.println("BACK: Peer started."); // TODO add more info
			peer();
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
	
	private static void initRMI(String accessPoint) {
		
        try {
        	
            Peer obj = new Peer();
            RMITesting stub = (RMITesting) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(accessPoint, stub);

            //System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
	}

	private static void initPeer() throws SocketException {

		// Initialize socket and broadcast message
		DatagramSocket socket = new DatagramSocket();
		ServiceMessage msg = new ServiceMessage();
		msg.putChunk("1.0", "1", "blah", "0", "2");
		
		DatagramPacket broadcastPacket = new DatagramPacket(msg.getMessage().getBytes(), msg.getMessage().getBytes().length, mdbAddr, mdbPort);

		// Repeat message every second
		Timer repeatMsg = new Timer();

		repeatMsg.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {

				try {

					System.out.println("INIT: sent \"" + msg.getMessage() +"\"");
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

		System.out.println("size: " + dataPacket.getLength());
		System.out.println("offset: " + dataPacket.getOffset());
		
		for(byte datas : dataPacket.getData()) {
			
			System.out.print(datas);
		}
		
		String msg = new String(dataPacket.getData());
		msg = msg.trim();
		System.out.println(msg);
		
		multi.close();
	}
}