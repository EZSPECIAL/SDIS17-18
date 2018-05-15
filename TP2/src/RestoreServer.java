import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RestoreServer implements Runnable {

	private ServerSocket server;
	private int maxConnections;
	private ExecutorService executor;
	
	// TODO doc
	public RestoreServer(int maxConnections, int peerID) {
		
		this.maxConnections = maxConnections;
		this.executor = Executors.newFixedThreadPool(this.maxConnections);
		
		try {
			this.server = new ServerSocket((Peer.restoreBasePort + (peerID - 1) * 10) + 1); // TODO add to Peer as function
		} catch (IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception creating server socket!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
		}
	}

	// TODO doc
	private void waitForClient() throws IOException {
		
		// TODO time out when all chunks received
		// TODO add CHUNK message again
		while(true) {
			
			// Wait for client connection
			Socket s = server.accept();
			SystemManager.getInstance().logPrint("client connected", SystemManager.LogLevel.VERBOSE);
			
			this.executor.submit(new RestoreServerThread(s));
		}
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("TCP Server " + Thread.currentThread().getId());
		
		try {
			this.waitForClient();
		} catch (IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception listening on server socket!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
	}
}
