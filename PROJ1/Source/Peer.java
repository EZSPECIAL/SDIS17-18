import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Peer implements RMITesting {

	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	
	// Addresses and ports for multicast channels
	private InetAddress mccAddr;
	private InetAddress mdbAddr;
	private InetAddress mdrAddr;
	
	private int mccPort;
	private int mdbPort;
	private int mdrPort;
	
	// Sockets for multicast channels
	private MulticastSocket mccSocket;
	private MulticastSocket mdbSocket;
	private MulticastSocket mdrSocket;
	
	// TODO document
	public Peer(String protocolVersion, int peerID, String accessPoint, InetAddress mccAddr, int mccPort, InetAddress mdbAddr, int mdbPort, InetAddress mdrAddr, int mdrPort) {
		
		this.protocolVersion = protocolVersion;
		this.peerID = peerID;
		this.accessPoint = accessPoint;
		this.mccAddr = mccAddr;
		this.mdbAddr = mdbAddr;
		this.mdrAddr = mdrAddr;
		this.mccPort = mccPort;
		this.mdbPort = mdbPort;
		this.mdrPort = mdrPort;
		
		try {
			this.mccSocket = new MulticastSocket(mccPort);
			this.mdbSocket = new MulticastSocket(mdbPort);
			this.mdrSocket = new MulticastSocket(mdrPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
            SystemManager.getInstance().logPrint(this.peerID, msg, SystemManager.LogLevel.DEBUG);
        } catch(Exception e) {
        	
            String msg = "RMI exception: " + e.toString();
            SystemManager.getInstance().logPrint(this.peerID, msg, SystemManager.LogLevel.DEBUG);
            e.printStackTrace();
        }
	}
	
	@Override
	public void remoteBackup(String filepath, int repDeg) throws RemoteException {
		
		// TODO backup protocol
		
		String msg = "backup: " + filepath + " - " + repDeg;
		SystemManager.getInstance().logPrint(this.peerID, msg, SystemManager.LogLevel.NORMAL);
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
