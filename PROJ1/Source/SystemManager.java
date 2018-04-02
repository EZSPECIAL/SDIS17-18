import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SystemManager {

	// Enumerator classes for logging management.
	public enum LogLevel {NONE, NORMAL, SERVICE_MSG, DEBUG, VERBOSE}
	public enum LogMethod {CONSOLE, FILE, BOTH}
	
	private static SystemManager singleton = new SystemManager();
	private static int peerID;
	
	// Log management block
	private static LogLevel logLevel = LogLevel.SERVICE_MSG;
	private static LogMethod logMethod = LogMethod.CONSOLE;
	private static final String logFolder = "logFiles";
	private static final String logPrefix = "PeerLog_";
	private static final String consoleLogFormat = "%-16s - %s\n";
	private static final String fileLogFormat = "%s - %-16s - %s";
	
	/**
	 * Private constructor for singleton pattern.
	 */
	private SystemManager() {}
	
	/**
	 * @return the singleton instance of the class
	 */
	public static SystemManager getInstance( ) {
		return singleton;
	}
	
	/**
	 * Initialises logging management with the specified logging level and method.
	 * 
	 * @param logLevel the desired logging level (normal / debug / verbose)
	 * @param logMethod the desired logging method (file / console / both)
	 */
	public void initLog(LogLevel logLevel, LogMethod logMethod) {
		
		// Create log file directory if it doesn't exist
	    File directory = new File(logFolder);
	    if(!directory.exists()) {
	        directory.mkdir();
	    }
	    
	    SystemManager.logLevel = logLevel;
	    SystemManager.logMethod = logMethod;
	}
	
	/**
	 * @param peerID numeric value of Peer ID to set
	 */
	public void setPeerID(int peerID) {
		SystemManager.peerID = peerID;
	}
	
	/**
	 * Handles logging of messages, prints only if the log level matches the current log level and
	 * can print to console and/or a log file according to the current settings.
	 * 
	 * @param peerID ID of the Peer that requested the log
	 * @param message string to log
	 * @param desiredLogLevel log level of this message
	 */
	public synchronized void logPrint(String message, LogLevel desiredLogLevel) {
		
		if(logLevel.equals(LogLevel.NONE)) return;

		// Console printing handling
		if(logMethod.equals(LogMethod.CONSOLE) || logMethod.equals(LogMethod.BOTH)) {
			if(logLevel.ordinal() >= desiredLogLevel.ordinal()) {
				
				System.out.printf(consoleLogFormat, Thread.currentThread().getName(), message);
				System.out.flush();
			}
		}
		
		// Log file printing handling
		if(logMethod.equals(LogMethod.CONSOLE)) return;
		if(logLevel.ordinal() < desiredLogLevel.ordinal()) return;
		
		// Create path for log file
		String filepath = "./" + logFolder + "/" + logPrefix + SystemManager.peerID + ".txt";
		File toCreate = new File(filepath);
		Path toWrite = Paths.get(filepath);

		// Get current day and time and append to log message
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();

		String toPrint = String.format(fileLogFormat, dateFormat.format(date), Thread.currentThread().getName(), message);
		List<String> lines = Arrays.asList(toPrint);
		
		// Create file only if it doesn't exist and append new lines to it
		try {
			toCreate.createNewFile();
			Files.write(toWrite, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
		} catch(IOException e) {
			if(logLevel.equals(LogLevel.VERBOSE)) {
				System.out.println("IO exception on log write");
				e.printStackTrace();
				System.out.flush();
			}
		}
	}
	
}