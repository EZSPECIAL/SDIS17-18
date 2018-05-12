import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer implements RMITesting {

	// Public general constants
	public static final int baseTimeoutMS = 1000;
	public static final int maxAttempts = 5;
	public static final int minResponseWaitMS = 0;
	public static final int maxResponseWaitMS = 400;
	public static final int consecutiveMsgWaitMS = 100;

	public static final String storageFolderName = "Storage";
	public static final String peerFolderPrefix = "Peer_";
	public static final String restoredFolderName = "Restored";
	public static final String restoredSuffix = "_restoredBy";
	
	// Public header indices
	public static final int protocolI = 0;
	public static final int protocolVersionI = 1;
	public static final int senderI = 2;
	public static final int hashI = 3;
	public static final int chunkNoI = 4;
	public static final int repDegI = 5;
	
	// Private constants
	private static final int executorThreadsMax = 15;
	private static final int baseRestoreTimeoutMS = 800;
	
	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	private long maxDiskSpace = 5000;
	
	// Sockets for multicast channels
	private ServiceChannel mcc;
	private ServiceChannel mdb;
	private ServiceChannel mdr;
	
	private SystemDatabase database = new SystemDatabase();
	private ConcurrentHashMap<String, ProtocolState> protocols = new ConcurrentHashMap<String, ProtocolState>(8, 0.9f, 1);
	private ScheduledExecutorService executor = Executors.newScheduledThreadPool(executorThreadsMax);

	private static Peer singleton = new Peer();
	
	/**
	 * Private constructor for singleton pattern.
	 */
	private Peer() {}
	
	/**
	 * @return the singleton instance of the class
	 */
	public static Peer getInstance( ) {
		return singleton;
	}
	
	/**
	 * Initialises singleton Peer object. A Peer object handles protocol initiation and also requests for protocol handling from other initiators on known UDP multicast channels.
	 * Peers have the function of managing a file backup service and store information about their own database and what they believe the system currently has stored.
	 * 
	 * @param protocolVersion the backup system version
	 * @param peerID the numeric identifier of the Peer
	 * @param accessPoint service access point (RMI Object name)
	 * @param mccAddr address of the multicast control channel
	 * @param mccPort port for the multicast control channel
	 * @param mdbAddr address of the multicast data backup channel
	 * @param mdbPort port for the multicast data backup channel
	 * @param mdrAddr address of the multicast data restore channel
	 * @param mdrPort port for the multicast data restore channel
	 */
	public void initPeer(String protocolVersion, int peerID, String accessPoint, InetAddress mccAddr, int mccPort, InetAddress mdbAddr, int mdbPort, InetAddress mdrAddr, int mdrPort) {
		
		this.protocolVersion = protocolVersion;
		this.peerID = peerID;
		this.accessPoint = accessPoint;
		
		this.mcc = new ServiceChannel(mccAddr, mccPort, "mcc");
		this.mdb = new ServiceChannel(mdbAddr, mdbPort, "mdb");
		this.mdr = new ServiceChannel(mdrAddr, mdrPort, "mdr");
		
		new Thread(null, this.mcc, "control channel").start();
		new Thread(null, this.mdb, "backup channel").start();
		new Thread(null, this.mdr, "recovery channel").start();
		
		this.initRMI();
	}
	
	/**
	 * Register remote object in RMI registry so it can be called by TestApp 
	 */
	public void initRMI() {
		
        try {
        	RMITesting stub = (RMITesting) UnicastRemoteObject.exportObject(this, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(this.accessPoint, stub);

            String msg = "RMI started with remote name \"" + this.accessPoint + "\"";
            SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);
        } catch(Exception e) {
        	
            String msg = "RMI exception: " + e.toString();
            SystemManager.getInstance().logPrint(msg, SystemManager.LogLevel.DEBUG);
            e.printStackTrace();
        }
	}

	/**
	 * Creates directory specified by path if it doesn't already exist.
	 * 
	 * @param dirPath the directory path to create
	 */
	public void createDirIfNotExists(String dirPath) {
		
	    File directory = new File(dirPath);
	    if(!directory.exists()) {
	        directory.mkdir();
	    }
	}
	
	/**
	 * Calculates the amount of KB that the Peer storage area is using.
	 * 
	 * @return the amount of KB being used by the Peer storage area
	 */
	public long getUsedSpace() {
		
		String storageFolder = "../" + Peer.storageFolderName;
		String peerFolder = storageFolder + "/" + Peer.peerFolderPrefix + this.peerID;
		
		Path folder = Paths.get(peerFolder);
		
		long size;
		try {
			size = Files.walk(folder)
					.filter(p -> p.toFile().isFile())
					.mapToLong(p -> p.toFile().length())
					.sum();
		} catch(IOException e) {
			return 0;
		}
		
		return (long) (size / 1000);
	}

	@Override
	public void remoteBackup(String filepath, int repDeg) throws IOException, NoSuchAlgorithmException, InterruptedException {
		
		executor.execute(new BackupProtocol(filepath, repDeg));
	}

	@Override
	public void remoteRestore(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException {
		
		Thread.currentThread().setName("Restore " + Thread.currentThread().getId());
		
		ProtocolState state = new ProtocolState(new ServiceMessage());
		Long timeoutMS = state.getTotalChunks(filepath) * baseRestoreTimeoutMS;

		SystemManager.getInstance().logPrint("restore timeout: " + timeoutMS + "ms", SystemManager.LogLevel.VERBOSE);
		
		Future<?> handler = executor.submit(new RestoreProtocol(filepath));

		executor.schedule(() -> {
			handler.cancel(true);
		}, timeoutMS, TimeUnit.MILLISECONDS);
	}

	@Override
	public void remoteDelete(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException {

		executor.execute(new DeleteProtocol(filepath));
	}

	@Override
	public void remoteReclaim(long maxKB) throws RemoteException {
		
		this.maxDiskSpace = maxKB;
		executor.execute(new ReclaimProtocol());
	}

	@Override
	public void remoteGetInfo() throws RemoteException {
		
		executor.execute(new InfoProtocol());
	}

	/**
	 * @return the backup system version
	 */
	public String getProtocolVersion() {
		return protocolVersion;
	}

	/**
	 * @return the numeric identifier of the Peer
	 */
	public int getPeerID() {
		return peerID;
	}

	/**
	 * @return the maximum disk space for this Peer in KB
	 */
	public long getMaxDiskSpace() {
		return maxDiskSpace;
	}

	/**
	 * @return the multicast control channel
	 */
	public ServiceChannel getMcc() {
		return mcc;
	}

	/**
	 * @return the multicast data backup channel
	 */
	public ServiceChannel getMdb() {
		return mdb;
	}

	/**
	 * @return the multicast data restore channel
	 */
	public ServiceChannel getMdr() {
		return mdr;
	}

	/**
	 * @return the system database for this Peer
	 */
	public SystemDatabase getDatabase() {
		return database;
	}

	/**
	 * @return the currently running ProtocolState objects
	 */
	public ConcurrentHashMap<String, ProtocolState> getProtocols() {
		return protocols;
	}

	/**
	 * @return the scheduled executor service of the Peer
	 */
	public ScheduledExecutorService getExecutor() {
		return executor;
	}

}
