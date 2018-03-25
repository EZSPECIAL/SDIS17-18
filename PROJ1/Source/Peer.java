import java.rmi.RemoteException;

public class Peer implements RMITesting {

	/**
	 * Default constructor for Peer objects.
	 */
	public Peer() {}
	
	@Override
	public void remoteBackup(String filepath, int repDeg) throws RemoteException {
		// TODO Auto-generated method stub
		System.out.println(filepath + " | " + repDeg);
		return;
	}

	@Override
	public void remoteRestore(String filepath) throws RemoteException {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public void remoteDelete(String filepath) throws RemoteException {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public void remoteSetDiskSpace(int maxKB) throws RemoteException {
		// TODO Auto-generated method stub
		return;
	}

	@Override
	public String remoteGetInfo() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

}
