import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class BackupProtocol implements Runnable {

	private String filepath;
	private int repDeg;
	
	/**
	 * Runs a BACKUP protocol procedure with specified filepath and replication degree.
	 * 
	 * @param filepath the file path to backup
	 * @param repDeg desired replication degree
	 */
	public BackupProtocol(String filepath, int repDeg) {
		this.filepath = filepath;
		this.repDeg = repDeg;
	}

	@Override
	public void run() {

		Thread.currentThread().setName("Backup " + Thread.currentThread().getId());
		
		String backMsg = "backup: " + this.filepath + " - " + this.repDeg;
		SystemManager.getInstance().logPrint("started " + backMsg, SystemManager.LogLevel.NORMAL);

		Peer peer = Peer.getInstance();

		// Initialise protocol state
		String key = null;
		try {
			key = this.initializeProtocolInstance(peer);
		} catch(NoSuchAlgorithmException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception on backup protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		if(key == null) {
			SystemManager.getInstance().logPrint("cannot backup files larger than 64GB!", SystemManager.LogLevel.NORMAL);
			return;
		}

		ProtocolState state = peer.getProtocols().get(key);
		
		// PUTCHUNK message loop
		try {
			if(this.putchunkLoop(peer, state)) SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
			else SystemManager.getInstance().logPrint("failed " + backMsg, SystemManager.LogLevel.NORMAL);
		} catch(InterruptedException | IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception or thread interruption on backup protocol!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			peer.getProtocols().remove(key);
			SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
			return;
		}
		
		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
	}
	
	/**
	 * Initialises the ProtocolState object relevant to this backup procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return whether initialisation was successful
	 */
	private String initializeProtocolInstance(Peer peer) throws NoSuchAlgorithmException, IOException {
		
		ProtocolState state = new ProtocolState(ProtocolState.ProtocolType.BACKUP, new ServiceMessage());
		
		if(!state.initBackupState(peer.getProtocolVersion(), this.filepath, this.repDeg)) return null;
		
		String protocolKey = peer.getPeerID() + state.getHashHex() + state.getProtocolType().name();
		peer.getProtocols().put(protocolKey, state);
		
		SystemManager.getInstance().logPrint("key inserted: " + protocolKey, SystemManager.LogLevel.VERBOSE);
		return protocolKey;
	}

	/**
	 * Sends the required PUTCHUNK messages for backing up a file and waits for the reception of repDeg STORED
	 * messages in response to each PUTCHUNK.
	 * 
	 * @param peer the singleton Peer instance
	 * @param state the Protocol State object relevant to this operation
	 * @return whether the backup was successful
	 */
	private boolean putchunkLoop(Peer peer, ProtocolState state) throws InterruptedException, IOException {
		
		while(state.getAttempts() < Peer.maxAttempts) {
			
			// Prepare and send next PUTCHUNK message
			byte[] msg = state.getParser().createPutchunkMsg(peer.getPeerID(), state);
			peer.getMdb().send(msg);

			int timeoutMS = (int) (Peer.baseTimeoutMS * Math.pow(2, state.getAttempts()));
			Thread.sleep(timeoutMS);

			// Check if expected unique STORED messages were received
			if(state.isStoredCountCorrect()) {
				state.incrementCurrentChunkNo();
				state.resetStoredCount();
			} else {
				SystemManager.getInstance().logPrint("not enough STORED messages whithin " + timeoutMS + "ms", SystemManager.LogLevel.DEBUG);
				state.incrementAttempts();
			}

			if(state.isFinished()) return true;
		}
		
		return false;
	}
}