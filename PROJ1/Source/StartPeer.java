import java.net.*;

public class StartPeer {

	// Indices for command line parameters
	private static final int versionI = 0;
	private static final int peerI = 1;
	private static final int accessPointI = 2;
	private static final int mccI = 3;
	private static final int mdbI = 4;
	private static final int mdrI = 5;
	private static final int logLevelI = 6;
	private static final int logMethodI = 7;
	
	/**
	 * Starts a Peer for a distributed backup system with the specified arguments. Peer is started by
	 * validating the command line arguments and initialising the Peer object which also initialises the
	 * RMI thread and the channel listener threads.
	 * 
	 * @param args 1.  protocol version
	 * @param args 2.  peer ID
	 * @param args 3.  service access point (RMI Object name)
	 * @param args 4.  multicast control channel IP:port
	 * @param args 5.  multicast data backup IP:port
	 * @param args 6.  multicast data recovery IP:port
	 * @param args 7.  logging level
	 * @param args 8.  logging method
	 */
	public static void main(String[] args) {
		parseArguments(args);
	}
	
	/**
	 * Parses and validates command line arguments, sets up logging management and creates the Peer object.
	 * 
	 * @param args command line arguments received
	 */
	private static void parseArguments(String[] args) {
		
		if(args.length != 6 && args.length != 8) cmdErr("wrong number of arguments!");
		
		// Parse protocol version
		if(!args[versionI].equals("1.0") && !args[versionI].equals("1.1")) printErrExit("protocol version must be 1.0 or 1.1!");
		
		// Parse peer ID	
		int peerID = validateInt(args[peerI], "peer ID must be a non zero positive number!");
		if(peerID <= 0) printErrExit("peer ID must be a non zero positive number!");
		
		// Parse multicast channels' hostnames and ports
		InetAddress mccAddr = parseChannelHost(args[mccI], "mcc");
		int mccPort = parseChannelPort(args[mccI], "mcc");
		
		InetAddress mdbAddr = parseChannelHost(args[mdbI], "mdb");
		int mdbPort = parseChannelPort(args[mdbI], "mdb");
		
		InetAddress mdrAddr = parseChannelHost(args[mdrI], "mdr");
		int mdrPort = parseChannelPort(args[mdrI], "mdr");
		
		if(mccPort == mdbPort || mccPort == mdrPort || mdbPort == mdrPort) printErrExit("multicast channel ports must be different!");
		
		// Parse optional parameters for logging options
		if(args.length == 8) {
			
			SystemManager.LogLevel logLevel = null;
			try {
				logLevel = SystemManager.LogLevel.valueOf(args[logLevelI].toUpperCase());
			} catch(IllegalArgumentException e) {
				printErrExit("invalid logging level \"" + args[logLevelI].toUpperCase() + "\", must be one of [NONE, NORMAL, DEBUG, VERBOSE]!");
			}
			
			SystemManager.LogMethod logMethod = null;
			try {
				logMethod = SystemManager.LogMethod.valueOf(args[logMethodI].toUpperCase());
			} catch(IllegalArgumentException e) {
				printErrExit("invalid logging method \"" + args[logMethodI].toUpperCase() + "\", must be one of [CONSOLE, FILE, BOTH]!");
			}
			
			SystemManager.getInstance().initLog(logLevel, logMethod);
		}
		
		SystemManager.getInstance().setPeerID(peerID);
		Peer.getInstance().initPeer(args[versionI], peerID, args[accessPointI], mccAddr, mccPort, mdbAddr, mdbPort, mdrAddr, mdrPort);
	}
	
	/**
	 * Parses a hostname in IP:port format, exits program if hostname is invalid.
	 * 
	 * @param s the hostname string
	 * @param channelName the channel name
	 * @return InetAddress of the host
	 */
	private static InetAddress parseChannelHost(String s, String channelName) {
		
		String[] split = s.split(":");
		if(split.length != 2) printErrExit(channelName + " must be in \"IP:port\" format!");
		
		return validateHost(split[0]);
	}
	
	/**
	 * Parses a port in IP:port format, exits program if port is invalid.
	 * 
	 * @param s port
	 * @param channelName the channel name
	 * @return port as integer
	 */
	private static int parseChannelPort(String s, String channelName) {
		
		String[] split = s.split(":");
		if(split.length != 2) printErrExit(channelName + " must be in \"IP:port\" format!");
		
		int port = validateInt(split[1], channelName + " port must be a number between 1024 and 49151!");
		
		if(port < 1024 || port > 49151) printErrExit(channelName + " port must be a number between 1024 and 49151!");
		
		return port;
	}
	
	/**
	 * Attempts to convert string to int and exits program if conversion is not possible.
	 * 
	 * @param s string to convert
	 * @param errMsg error message to print
	 * @return converted integer
	 */
	private static int validateInt(String s, String errMsg) {
		
		int num = 0;
		try {
			num = Integer.parseInt(s);
		} catch(NumberFormatException e) {
			printErrExit(errMsg);
		}
		
		return num;
	}
	
	/**
	 * Validates hostname as multicast address and exits program if invalid.
	 * 
	 * @param s the multicast adress string
	 * @return InetAddress of the host
	 */
	private static InetAddress validateHost(String s) {
		
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(s);
		} catch (UnknownHostException e) {
			printErrExit("unknown host \"" + s + "\"!");
		}
		
		// Validate multicast address
		if(!addr.isMulticastAddress() || addr.isMCLinkLocal()) printErrExit(addr.getHostAddress() + " is not a valid multicast address! Must be from 224.0.1.0 to 239.255.255.255.");

		return addr;
	}
	
	/**
	 * Prints error message and program usage. Exits program with error code -1.
	 * 
	 * @param message error message to print
	 * @param protocol protocol being executed
	 */
	private static void cmdErr(String message) {
		
		System.out.println("StartPeer: " + message);
		
		System.out.println("Regular usage:");
		System.out.println("\t java StartPeer 1.0 1 Peer1 224.0.0.1:1500 224.0.0.2:1600 224.0.0.3:1700");
		System.out.println("\t java StartPeer 1.1 1 Peer1 224.0.0.1:1500 224.0.0.2:1600 224.0.0.3:1700");
		System.out.println("Logging options:");
		System.out.println("\t java StartPeer 1.0 1 Peer1 224.0.0.1:1500 224.0.0.2:1600 224.0.0.3:1700 <logLevel> <logMethod>");
		System.out.println("\t <logLevel> - NONE, NORMAL, DEBUG, VERBOSE");
		System.out.println("\t <logMethod> - CONSOLE, FILE, BOTH");
		
		System.exit(-1);
	}
	
	/**
	 * Prints error message and exits with error code -1.
	 * 
	 * @param message error message to print
	 */
	private static void printErrExit(String message) {
		
		System.out.println("StartPeer: " + message);
		System.exit(-1);
	}
	
}