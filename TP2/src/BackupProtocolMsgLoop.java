import java.io.IOException;

public class BackupProtocolMsgLoop implements Runnable {

	private Peer peer;
	private ProtocolState state;
	private BackupProtocol backupProtocol;
	private int attempts = 0;
	private long chunkNo;
	
	public BackupProtocolMsgLoop(Peer peer, ProtocolState state, Long currentChunkNo, BackupProtocol backupProtocol) {
		this.peer = peer;
		this.state = state;
		this.chunkNo = currentChunkNo;
		this.backupProtocol = backupProtocol;
	}
	
	/**
	 * Sends the required PUTCHUNK messages for backing up a chunk and waits for the reception of repDeg STORED
	 * messages in response to the PUTCHUNK.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @return whether enough responses were registered
	 */
	private boolean putchunkLoop() throws InterruptedException, IOException {
		
		while(true) {

			// Prepare and send next PUTCHUNK message
			byte[] msg;
			msg = state.getParser().createPutchunkMsg(this.peer.getPeerID(), this.state, this.chunkNo);
			peer.getMdb().send(msg);
			int timeoutMS = (int) (Peer.baseTimeoutMS * Math.pow(2, this.attempts));
			Thread.sleep(timeoutMS);

			this.attempts++;

			if(this.state.getRespondedID().get(this.chunkNo).size() >= this.state.getDesiredRepDeg()) {
				return true;
			} else if(this.attempts >= Peer.maxAttempts) {
				this.backupProtocol.setNotEnoughResponses(true);
				return false;
			}

			SystemManager.getInstance().logPrint("not enough STORED messages whithin " + timeoutMS + "ms", SystemManager.LogLevel.DEBUG);
		}
	}

	@Override
	public void run() {
		
		Thread.currentThread().setName("Backup loop " + Thread.currentThread().getId());
		
		try {
			this.putchunkLoop();
		} catch(InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on backup protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
	}
	
}
