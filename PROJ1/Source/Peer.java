import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;

public class Peer implements RMITesting {

	// Peer info
	private String protocolVersion;
	private int peerID;
	private String accessPoint;
	
	// Sockets for multicast channels
	private ServiceChannel mcc;
	private ServiceChannel mdb;
	private ServiceChannel mdr;
	
	private ProtocolState currProtocol; // ASK concurrent hash map?
	
	// DOC document
	public Peer(String protocolVersion, int peerID, String accessPoint, InetAddress mccAddr, int mccPort, InetAddress mdbAddr, int mdbPort, InetAddress mdrAddr, int mdrPort) {
		
		this.protocolVersion = protocolVersion;
		this.peerID = peerID;
		this.accessPoint = accessPoint;
		
		this.mcc = new ServiceChannel(mccAddr, mccPort, "mcc");
		this.mdb = new ServiceChannel(mdbAddr, mdbPort, "mdb");
		this.mdr = new ServiceChannel(mdrAddr, mdrPort, "mdr");
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
	
	// DOC document
	public void receiveLoop() throws IOException {
		
		while(true) {
			
			DatagramPacket packet = this.mdb.listen();
			ServiceMessage parser = new ServiceMessage();
			
			// Parse header/body
			if(!parser.findHeaderIndices(packet)) continue;
			String[] headerFields = parser.stripHeader(packet);
			byte[] bodyData = parser.stripBody(packet);
			
			// Create Peer storage area
		    File directory = new File("./Storage");
		    if(!directory.exists()) {
		        directory.mkdir();
		    }
		    
		    // TODO create directory for each file
		    // TODO do not store own chunks
		    
		    // Output data to file
			FileOutputStream output;
			try {
				output = new FileOutputStream("./Storage/" + headerFields[3] + "." + headerFields[4]); // TODO use Peer ID for folders
			} catch (FileNotFoundException e) {
				// CATCH Auto-generated catch block
				e.printStackTrace();
				continue;
			}

			SystemManager.getInstance().logPrint("writing file", SystemManager.LogLevel.DEBUG);
			
			try {
				output.write(bodyData);
				output.close();
			} catch (IOException e) {
				// CATCH Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void remoteBackup(String filepath, int repDeg) throws IOException, NoSuchAlgorithmException {
		
		// TODO backup protocol
		
		String backStarted = "backup: " + filepath + " - " + repDeg;
		SystemManager.getInstance().logPrint(backStarted, SystemManager.LogLevel.NORMAL);
		
		this.currProtocol = new ProtocolState(ProtocolState.ProtocolType.INIT_BACKUP);
		this.currProtocol.initBackupState(this.protocolVersion, filepath, repDeg);
		
		// Prepare and send next PUTCHUNK message
		ServiceMessage sMsg = new ServiceMessage();
		byte[] msg = sMsg.putChunk(this.peerID, this.currProtocol);
		
		this.mdb.send(msg);
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
