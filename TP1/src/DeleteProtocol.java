import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class DeleteProtocol implements Runnable {

	private String filepath;
	
	/**
	 * Runs a DELETE protocol procedure with specified filepath.
	 * 
	 * @param filepath the file path to delete
	 */
	public DeleteProtocol(String filepath) {
		this.filepath = filepath;
	}
	
	@Override
	public void run() {

		Thread.currentThread().setName("Delete " + Thread.currentThread().getId());
		
		String delMsg = "delete: " + filepath;
		SystemManager.getInstance().logPrint("started " + delMsg, SystemManager.LogLevel.NORMAL);
		
		// Initialise protocol state for deleting file
		Peer peer = Peer.getInstance();
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.DELETE, new ServiceMessage());
		try {
			state.initDeleteState(peer.getProtocolVersion(), filepath);
		} catch(NoSuchAlgorithmException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception on delete protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		// Create and send DELETE message 3 times
		byte[] msg = state.getParser().createDeleteMsg(peer.getPeerID(), state);
		
		try {
			peer.getMcc().send(msg);
			Thread.sleep(Peer.consecutiveMsgWaitMS);
			peer.getMcc().send(msg);
			Thread.sleep(Peer.consecutiveMsgWaitMS);
			peer.getMcc().send(msg);
		} catch(InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on delete protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		SystemManager.getInstance().logPrint("finished " + delMsg, SystemManager.LogLevel.NORMAL);
	}
	
}