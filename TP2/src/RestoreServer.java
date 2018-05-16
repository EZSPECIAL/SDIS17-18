import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RestoreServer implements Runnable {

	private static final int threadMargin = 5;
	
	private ServerSocket server;
	private ExecutorService executor;

	/**
	 * Handles server for receiving CHUNK messages from clients.
	 * 
	 * @param server the server socket
	 * @param maxConnections max connections allowed for this server socket
	 */
	public RestoreServer(ServerSocket server, int maxConnections) {

		this.executor = Executors.newFixedThreadPool(maxConnections + threadMargin);
		this.server = server;
	}

	/**
	 * Waits for a client connection and then spawns a worker thread
	 * for handling the client message.
	 */
	private void waitForClient() {
		
		// Wait for client connections until parent thread closes socket
		while(true) {

			Socket s;
			try {
			s = server.accept();
			} catch(IOException e) {
				SystemManager.getInstance().logPrint("server closed", SystemManager.LogLevel.VERBOSE);
				return;
			}
			
			SystemManager.getInstance().logPrint("client connected", SystemManager.LogLevel.VERBOSE);
			this.executor.submit(new RestoreServerThread(s));
		}
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("TCP Server " + Thread.currentThread().getId());
		this.waitForClient();
		this.executor.shutdown();
	}
}
