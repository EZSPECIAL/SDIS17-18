import java.rmi.RemoteException;

public class Peer implements RMITesting {

	/**
	 * Default constructor for Peer objects.
	 */
	public Peer() {}
	
	@Override
	public void remoteBackup(String filepath, int repDeg) throws RemoteException {
		
		// TODO backup protocol
		// TODO proper log message
		
		String msg = "backup: " + filepath + " | " + repDeg;
		System.out.println(msg);
		StartPeer.getInstance().printToLog(1, msg);
		return;
	}

	@Override
	public void remoteRestore(String filepath) throws RemoteException {
		
		// TODO restore protocol
		// TODO proper log message
		
		System.out.println("restore: " + filepath);
		return;
	}

	@Override
	public void remoteDelete(String filepath) throws RemoteException {
		
		// TODO delete protocol
		// TODO proper log message
		
		System.out.println("delete: " + filepath);
		return;
	}

	@Override
	public void remoteReclaim(int maxKB) throws RemoteException {
		
		// TODO reclaim protocol
		// TODO proper log message
		
		System.out.println("reclaim: " + maxKB);
		return;
	}

	@Override
	public String remoteGetInfo() throws RemoteException {
		
		// TODO info protocol
		// TODO proper log message
		
		System.out.println("info");
		return null;
	}

}
