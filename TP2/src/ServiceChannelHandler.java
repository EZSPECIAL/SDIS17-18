import java.net.DatagramPacket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ServiceChannelHandler implements Runnable {

	private static final int executorThreadsMax = 15;

	private ExecutorService executor = Executors.newFixedThreadPool(executorThreadsMax);
	private String channelName;
	private ServiceChannel channel;
	
	/**
	 * Checks for new service messages received and submits to thread pool for processing.
	 * 
	 * @param channel the ServiceChannel object responsible for this handler
	 * @param channelName the channel name
	 */
	public ServiceChannelHandler(ServiceChannel channel, String channelName) {
		this.channel = channel;
		this.channelName = channelName;
	}
	
	@Override
	public void run() {
		
		while(true) {

			// Wait until a new message is available, does not use CPU
			DatagramPacket packet;
			try {
				packet = this.channel.getMessages().take();
			} catch (InterruptedException e) {
				SystemManager.getInstance().logPrint("handler thread interrupted!", SystemManager.LogLevel.NORMAL);
				e.printStackTrace();
				return;
			}
			
			// Wait if no slot available in thread pool
			while(((ThreadPoolExecutor) executor).getActiveCount() == ServiceChannelHandler.executorThreadsMax);
			
			executor.submit(new SystemHandler(packet, this.channelName));
		}
	}
}