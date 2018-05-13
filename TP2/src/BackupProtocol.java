import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BackupProtocol implements Runnable {

	private static final int executorThreadsMax = 5;
	
	private String filepath;
	private int repDeg;
	private boolean notEnoughResponses = false;
	private ExecutorService executor = Executors.newFixedThreadPool(executorThreadsMax);
	private HashSet<Future<?>> tasks = new HashSet<Future<?>>();
	
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
		
		// Submit threads for running a PUTCHUNK message for the current chunk
		while(!state.isFinished()) {
		
			// Wait if no slot available in thread pool
			while(this.tasks.size() >= BackupProtocol.executorThreadsMax) {
				this.tasks.removeIf(t -> t.isDone());
			}

			Future<?> task = this.executor.submit(new BackupProtocolMsgLoop(peer, state, state.getCurrentChunkNo(), this));
			this.tasks.add(task);
			state.incrementCurrentChunkNo();
		}
		
		// Wait until all threads complete
		while(this.tasks.size() != 0) {
			this.tasks.removeIf(t -> t.isDone());
		}
		
		if(!this.notEnoughResponses) SystemManager.getInstance().logPrint("finished " + backMsg, SystemManager.LogLevel.NORMAL);
		else SystemManager.getInstance().logPrint("failed " + backMsg + ", replication degree lower than desired", SystemManager.LogLevel.NORMAL);

		peer.getDatabase().backupUpdate(state);
		peer.getProtocols().remove(key);
		SystemManager.getInstance().logPrint("key removed: " + key, SystemManager.LogLevel.VERBOSE);
	}
	
	/**
	 * Initialises the ProtocolState object relevant to this backup procedure.
	 * 
	 * @param peer the singleton Peer instance
	 * @return the key relevant to this protocol, null on error
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
	 * @param notEnoughResponses whether enough responses were received for this backup protocol
	 */
	public void setNotEnoughResponses(boolean notEnoughResponses) {
		this.notEnoughResponses = notEnoughResponses;
	}
}