import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

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
	 */
	void remoteBackup(String filepath, int repDeg) throws IOException, NoSuchAlgorithmException, InterruptedException;
	
	/**
	 * Triggers the restore protocol with the invoking Peer as Initiator Peer.
	 * 
	 * @param filepath path to file to backup
	 */
	void remoteRestore(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException;
	
	/**
	 * Triggers the delete protocol with the invoking Peer as Initiator Peer.
	 * 
	 * @param filepath path to file to backup
	 */
	void remoteDelete(String filepath) throws IOException, NoSuchAlgorithmException, InterruptedException;
	
	/**
	 * Sets max disk space the Peer is allowed to use.
	 * 
	 * @param maxKB max kilobytes (K = 1000) the Peer can use
	 */
	void remoteReclaim(long maxKB) throws RemoteException;
	
	/**
	 * Peer sends all the state info to the Client.
	 */
	void remoteGetInfo() throws RemoteException;
}