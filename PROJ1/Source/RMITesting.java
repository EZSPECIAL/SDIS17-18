import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface used for RMI of backup service methods.
 * <br><br>
 * Allows usage of backup, restore, delete and reclaim protocol, as well as
 * getting general info about a Peer and setting max disk usage of a Peer.
 */
public interface RMITesting extends Remote {
	
	/**
	 * Triggers the backup protocol with the invoking Peer as Initiator Peer.
	 * 
	 * @param filepath path to file to backup
	 * @param repDeg desired number of copies of the file in the system
	 * @throws RemoteException
	 */
	void remoteBackup(String filepath, int repDeg) throws RemoteException;
	
	/**
	 * Triggers the restore protocol with the invoking Peer as Initiator Peer.
	 * 
	 * @param filepath path to file to backup
	 * @throws RemoteException
	 */
	void remoteRestore(String filepath) throws RemoteException;
	
	/**
	 * Triggers the delete protocol with the invoking Peer as Initiator Peer.
	 * 
	 * @param filepath path to file to backup
	 * @throws RemoteException
	 */
	void remoteDelete(String filepath) throws RemoteException;
	
	/**
	 * Sets max disk space the Peer is allowed to use.
	 * 
	 * @param maxKB max kilobytes (K = 1000) the Peer can use
	 * @throws RemoteException
	 */
	void remoteSetDiskSpace(int maxKB) throws RemoteException;
	
	/**
	 * Peer sends all the state info to the Client.
	 * 
	 * @throws RemoteException
	 */
	String remoteGetInfo() throws RemoteException;
}