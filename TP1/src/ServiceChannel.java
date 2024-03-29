import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServiceChannel implements Runnable {

	private static final int packetSize = 65000;
	private static final int executorThreadsMax = 20;

	// Multicast socket settings
	private InetAddress addr;
	private int port;
	private String channelName;
	private MulticastSocket socket;

	private ExecutorService executor = Executors.newFixedThreadPool(executorThreadsMax);

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
		} catch(IOException e) {

			String msg = "failed to open socket with name \"" + channelName + "\"";
			SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Wait for a packet on the UDP socket and returns it.
	 * 
	 * @return the packet received
	 */
	public DatagramPacket listen() throws IOException {

		byte[] binData = new byte[packetSize];
		DatagramPacket packet = new DatagramPacket(binData, binData.length);

		String msg = "receiving packets on \"" + this.channelName + "\"";
		SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);

		socket.joinGroup(this.addr);
		socket.receive(packet);
		socket.leaveGroup(this.addr);

		return packet;
	}

	/**
	 * Sends a packet on the UDP socket.
	 * 
	 * @param data the data to send
	 */
	public synchronized void send(byte[] data) throws IOException {

		DatagramPacket packet = new DatagramPacket(data, data.length, this.addr, this.port);

		String sent = "sending packets on " + this.channelName;
		SystemManager.getInstance().logPrint(sent, SystemManager.LogLevel.DEBUG);

		this.socket.send(packet);
	}

	@Override
	public void run() {

		while(true) {

			DatagramPacket packet;
			try {
				packet = this.listen();
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("I/O Exception on backup protocol!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}

			// Submit to thread for processing
			executor.submit(new SystemHandler(packet, this.channelName));
		}
	}

}