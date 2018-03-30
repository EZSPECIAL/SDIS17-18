import java.io.File;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;

public class TestApp {

	// Remote object for invoking methods of
	private static RMITesting remoteObj = null;
	
	// Indices for command line parameters
	private static final int accessPointI = 0;
	private static final int protocolI = 1;
	private static final int opnd1I = 2;
	private static final int opnd2I = 3;
	
	private TestApp() {}

	/**
	 * Backup system testing program using RMI to invoke protocols on specific Peers.
	 *  
	 * @param args 1.  service access point (RMI Object name)
	 * @param args 2.  protocol to invoke
	 * @param args 3.  operand 1 (pathname, max KB if RECLAIM)
	 * @param args 4.  operand 2 (repDeg if BACKUP)
	 */
	public static void main(String[] args) {

		// Print program usage if no arguments were supplied
		if(args.length < 2) cmdErr("wrong argument number!", "all");

		// Get remote object for invoking methods
		remoteObj = getRMStub(args[accessPointI]);
		if(remoteObj == null) System.exit(-1);

		// Parse the protocol requested by user
		String protocol = args[protocolI].toLowerCase();

		switch(protocol) {

		// Validate filepath and replication degree and run remote backup method
		case "backup":

			if(args.length != 4) cmdErr("wrong argument number for BACKUP protocol!", "backup");
			if(!checkFilepath(args[opnd1I])) printErrExit("path specified does not exist or is a directory!");
			
			runBackup(args[opnd1I], parseRepDeg(args[opnd2I]));
			break;
			
		case "backupenh":
			// TODO remote method, confirm protocol version
			break;

		// Validate filepath and run remote restore method
		case "restore":
			
			if(args.length != 3) cmdErr("wrong argument number for RESTORE protocol!", "restore");
			if(!checkFilepath(args[opnd1I])) printErrExit("path specified does not exist or is a directory!");

			runRestore(args[opnd1I]);
			break;
			
		case "restoreenh":
			// TODO remote method, confirm protocol version
			break;
			
		// Validate filepath and run remote delete method
		case "delete":
			
			if(args.length != 3) cmdErr("wrong argument number for DELETE protocol!", "delete");
			if(!checkFilepath(args[opnd1I])) printErrExit("path specified does not exist or is a directory!");

			runDelete(args[opnd1I]);
			break;
			
		case "deleteenh":
			// TODO remote method, confirm protocol version
			break;
			
		// Validate max disk space parameter and run remote reclaim method
		case "reclaim":
			
			if(args.length != 3) cmdErr("wrong argument number for RECLAIM protocol!", "reclaim");
			
			runReclaim(parseMaxDiskSpace(args[opnd1I]));
			break;
			
		// Run remote system state method
		case "state":
			
			runState();
			break;
			
		// Didn't match with any of the known protocols
		default:
			cmdErr("unrecognized protocol \"" + args[protocolI] + "\"!", "all");
			break;
		}
	}

	/**
	 * Executes the BACKUP protocol with the specified filepath and replication degree.
	 * 
	 * @param filepath path to file to backup
	 * @param repDeg desired replication degree
	 */
	private static void runBackup(String filepath, int repDeg) {
		
		try {
			remoteObj.remoteBackup(filepath, repDeg);
		} catch(IOException e) {
			System.err.println("TestApp: IO exception executing remote backup " + e.toString());
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			System.err.println("TestApp: no such algorithm exception executing remote backup " + e.toString());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("TestApp: interrupted thread exception executing remote backup " + e.toString());
			e.printStackTrace();
		}
	}
	
	/**
	 * Executes the RESTORE protocol with the specified filepath.
	 * 
	 * @param filepath path to file to restore
	 */
	private static void runRestore(String filepath) {
		
		try {
			remoteObj.remoteRestore(filepath);
		} catch(RemoteException e) {
			System.err.println("TestApp: exception executing remote restore " + e.toString());
			e.printStackTrace();
		}
	}
	
	/**
	 * Executes the DELETE protocol with the specified filepath.
	 * 
	 * @param filepath path to file to delete
	 */
	private static void runDelete(String filepath) {
		
		try {
			remoteObj.remoteDelete(filepath);
		} catch(RemoteException e) {
			System.err.println("TestApp: exception executing remote delete " + e.toString());
			e.printStackTrace();
		}
	}
	
	/**
	 * Executes the RECLAIM protocol with the specified max KB of disk space.
	 * 
	 * @param maxKB max KB of disk space allowed for backing up files
	 */
	private static void runReclaim(int maxKB) {
		
		try {
			remoteObj.remoteReclaim(maxKB);
		} catch(RemoteException e) {
			System.err.println("TestApp: exception executing remote reclaim " + e.toString());
			e.printStackTrace();
		}
	}
	
	/**
	 * Executes the remote method for getting backup system info.
	 */
	private static void runState() {
		
//		try {
//			String result = remoteObj.remoteGetInfo(); // TODO handle result
//		} catch(RemoteException e) {
//			System.err.println("TestApp: exception executing remote reclaim " + e.toString());
//			e.printStackTrace();
//		}
	}
	
	/**
	 * Looks up the RMI registry for a object matching the specified access point and returns it.
	 * 
	 * @param accessPoint RMI Object name
	 * @return RMI Object to invoke methods of
	 */
	private static RMITesting getRMStub(String accessPoint) {

		//String host = (args.length < 1) ? null : args[0]; // TODO RMI over network
		try {
			Registry registry = LocateRegistry.getRegistry();
			RMITesting stub = (RMITesting) registry.lookup(accessPoint);
			return stub;

		} catch(NotBoundException e) {
			System.err.println("TestApp: \"" + accessPoint + "\" is not registered for RMI!");
		} catch(Exception e) {
			System.err.println("TestApp: exception looking up registry " + e.toString());
			e.printStackTrace();
		}

		return null;
	}
	
	/**
	 * Returns whether a filepath is valid or not.
	 * 
	 * @param filepath filepath to verify
	 * @return true if valid, false otherwise
	 */
	private static boolean checkFilepath(String filepath) {
		
		File f = new File(filepath);
		if(f.exists() && !f.isDirectory()) { 
		    return true;
		}
		
		return false;
	}

	/**
	 * Validates and parses replication degree for BACKUP protocol
	 * 
	 * @param repDeg string representing replication degree
	 * @return numeric replication degree
	 */
	private static int parseRepDeg(String repDeg) {
		
		// Check that string only has 1 character and that it is a digit
		if(repDeg.length() != 1) printErrExit("replication degree must be a number between 1 and 9!");
		if(!Character.isDigit(repDeg.charAt(0))) printErrExit("replication degree must be a number between 1 and 9!");
		
		// Convert and validate number range
		int degree = Integer.parseInt(repDeg);
		if(degree < 1 || degree > 9) printErrExit("replication degree must be a number between 1 and 9!");
		
		return degree;
	}
	
	/**
	 * Validates and parses the maximum KB allowed for backing up files.
	 * 
	 * @param maxSpace max disk space allowed for backing up (in KB)
	 * @return numeric value of max KB
	 */
	private static int parseMaxDiskSpace(String maxSpace) {
		
		int maxKB = 0;
		
		try {
			maxKB = Integer.parseInt(maxSpace);
		} catch(NumberFormatException e) {
			printErrExit("max KB must be a number between 0 and MAX_INT!");
		}
		
		if(maxKB < 0) printErrExit("max KB must be a number between 0 and MAX_INT!");
		
		return maxKB;
	}
	
	/**
	 * Prints error message and program usage. Exits program with error code -1.
	 * 
	 * @param message error message to print
	 * @param protocol protocol being executed
	 */
	private static void cmdErr(String message, String protocol) {
		
		System.err.println("TestApp: " + message);
		System.out.println("Example usage:");

		if(protocol.equals("all") || protocol.equals("backup")) System.out.println("\t java TestApp Peer1 BACKUP test1.pdf 3");
		if(protocol.equals("all") || protocol.equals("restore")) System.out.println("\t java TestApp Peer1 RESTORE test1.pdf");
		if(protocol.equals("all") || protocol.equals("delete")) System.out.println("\t java TestApp Peer1 DELETE test1.pdf");
		if(protocol.equals("all") || protocol.equals("reclaim")) System.out.println("\t java TestApp Peer1 RECLAIM 0");
		if(protocol.equals("all") || protocol.equals("state")) System.out.println("\t java TestApp Peer1 STATE");
		
		System.exit(-1);
	}
	
	/**
	 * Prints error message and exits with error code -1.
	 * 
	 * @param message error message to print
	 */
	private static void printErrExit(String message) {
		
		System.err.println("TestApp: " + message);
		System.exit(-1);
	}
}