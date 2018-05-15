import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class ServiceMessage {

	// Header/body constants
	private static final String lineTermination = "\r\n";
	private static final String headerTermination = "\r\n\r\n";
	private static final int headerTerminationSize = 4;
	private static final int dataSize = 64000;
	private static final int expectedVersionLen = 3;
	private static final int expectedHashLen = 64;
	
	private static final int minimumMsgLen = 2;
	private static final int putchunkMinMsgLen = 6;
	private static final int storedMinMsgLen = 5;
	private static final int deleteMinMsgLen = 4;
	private static final int deletedMinMsgLen = 4;
	private static final int getchunkMinMsgLen = 5;
	private static final int chunkMinMsgLen = 5;
	private static final int removedMinMsgLen = 5;
	private static final int startedMinMsgLen = 3;
	private static final int enhancedGetchunkMinMsgLen = 6;
	
	private static final int maxChunkNo = 1000000;
	private static final int minRepDeg = 1;
	private static final int maxRepDeg = 9;
	
	// General header indices
	private static final int protocolI = 0;
	private static final int protocolVersionI = 1;
	private static final int senderI = 2;
	private static final int hashI = 3;
	
	// Backup header indices
	private static final int backChunkNoI = 4;
	private static final int backRepDegI = 5;
	
	// Enhanced RESTORE header indices
	public static final int addressI = 5;
	
	// Strict service message parsing flag
	private static final boolean ignoreMinorErrors = false;
	
	// Message header termination indices
	private int lineEndI = 0;
	private int headerEndI = 0;

	/**
	 * Returns a service message with the following format: "PUTCHUNK &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt; &lt;ReplicationDegree&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @param chunkNo the chunk number relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createPutchunkMsg(int peerID, ProtocolState state, Long chunkNo) throws IOException {

		// Get binary file data
	    byte[] buf = new byte[dataSize];
		int nRead = this.getData(state.getFilepath(), chunkNo, buf);
		
        String readMsg = "putchunk nRead: " + nRead;
        SystemManager.getInstance().logPrint(readMsg, SystemManager.LogLevel.VERBOSE);
	    
	    // Merge header and body to single byte[]
        String header = "PUTCHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + chunkNo + " " + state.getDesiredRepDeg() + headerTermination;
	    		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		
		if(nRead <= 0) return header.getBytes();
		else return this.mergeByte(header.getBytes(), header.getBytes().length, buf, nRead);
	}

	/**
	 * Returns a service message with the following format: "STORED &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createStoredMsg(int peerID, ProtocolState state) {
		
		String header = "STORED " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
        return header.getBytes();
	}
	
	/**
	 * Returns a service message with the following format: "DELETE &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createDeleteMsg(int peerID, ProtocolState state) {
		
		String header = "DELETE " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		return header.getBytes();
	}
	
	/**
	 * Returns a service message with the following format: "DELETED &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createDeletedMsg(int peerID, ProtocolState state) {
		
		String header = "DELETED " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		return header.getBytes();
	}
	
	/**
	 * Returns a service message with the following format: "GETCHUNK &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createGetchunkMsg(int peerID, ProtocolState state) throws UnknownHostException {

		String header;
		// Create message according to protocol version
		if(state.getProtocolVersion().equals("1.0")) {
			header = "GETCHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + headerTermination;
		// Append local address to new header line
		} else {
			
			InetAddress addr = InetAddress.getLocalHost();
			int port = (Peer.restoreBasePort + (peerID - 1) * 10) + 1; // TODO add based on running protocols, 10 limit
			header = "GETCHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + lineTermination;
			header += addr.getHostAddress() + ":" + port + headerTermination;
		}
		
        SystemManager.getInstance().logPrint("sending: " + header.trim().replaceAll(lineTermination, " / "), SystemManager.LogLevel.SERVICE_MSG);
        return header.getBytes();
	}
	
	/**
	 * Returns a service message with the following format: "CHUNK &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createChunkMsg(int peerID, ProtocolState state) throws IOException {
		
		// Get binary file data
	    byte[] buf = new byte[dataSize];
		int nRead = this.getData(state.getFilepath(), 0, buf);
		
        SystemManager.getInstance().logPrint("chunk nRead: " + nRead, SystemManager.LogLevel.VERBOSE);
	    
	    // Merge header and body to single byte[]
		String header = "CHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		
		if(nRead <= 0) return header.getBytes();
		else return this.mergeByte(header.getBytes(), header.getBytes().length, buf, nRead);
	}
	
	/**
	 * Returns a service message with the following format: "REMOVED &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt;".
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createRemovedMsg(int peerID, ProtocolState state) throws IOException {

		String header = "REMOVED " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		return header.getBytes();
	}
	
	/**
	 * Returns a service message with the following format: "PUTCHUNK &lt;Version&gt; &lt;SenderID&gt; &lt;FileID&gt; &lt;ChunkNo&gt; &lt;ReplicationDegree&gt;".
	 * Used for RECLAIM protocol to send own Peer chunk data instead of accessing original file.
	 * 
	 * @param peerID the numeric identifier of the sending Peer
	 * @param state the Protocol State object relevant to this operation
	 * @return the binary data representing the message
	 */
	public byte[] createReclaimMsg(int peerID, ProtocolState state) throws IOException {
		
		// Get binary file data
	    byte[] buf = new byte[dataSize];
		int nRead = this.getData(state.getFilepath(), 0, buf);
		
        String readMsg = "putchunk nRead: " + nRead;
        SystemManager.getInstance().logPrint(readMsg, SystemManager.LogLevel.VERBOSE);
	    
	    // Merge header and body to single byte[]
        String header = "PUTCHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + " " + state.getDesiredRepDeg() + headerTermination;
	    		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		
		if(nRead <= 0) return header.getBytes();
		else return this.mergeByte(header.getBytes(), header.getBytes().length, buf, nRead);
	}
	
	/**
	 * Returns a service message with the following format: "STARTED &lt;Version&gt; &lt;SenderID&gt;".
	 *  
	 * @param peerID the numeric identifier of the sending Peer
	 * @param protocolVersion the backup system version
	 * @return
	 */
	public byte[] createStartedMsg(int peerID, String protocolVersion) {
		
		String header = "STARTED " + protocolVersion + " " + peerID + headerTermination;
		
        SystemManager.getInstance().logPrint("sending: " + header.trim(), SystemManager.LogLevel.SERVICE_MSG);
		return header.getBytes();
	}
	
	/**
	 * Merges two byte arrays by copying from the start of each one up to a specified length for each.
	 * The second array is appended to the end of the first one.
	 * 
	 * @param arr1 the first array to merge
	 * @param arr1Len the length to merge of the first array
	 * @param arr2 the second array to merge (appended to the first)
	 * @param arr2Len the length to merge of the second array
	 * @return the merged byte array
	 */
	private byte[] mergeByte(byte[] arr1, int arr1Len, byte[] arr2, int arr2Len) throws IOException {
		
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(arr1, 0, arr1Len);
	    output.write(arr2, 0, arr2Len);
	    byte[] merged = output.toByteArray();
	    output.close();
	    
	    return merged;
	}
	
	/**
	 * Copies a chunk of data to the array provided. Returns the actual amount of bytes read.
	 * 
	 * @param filepath the file path of the file to read a chunk from
	 * @param chunkNo the current chunk number
	 * @param data the array to fill
	 * @return the actual number of bytes read
	 */
	private int getData(String filepath, long chunkNo, byte[] data) throws IOException {

		FileInputStream input = new FileInputStream(filepath);
		input.skip(chunkNo * dataSize);
		int nRead = input.read(data, 0, dataSize);
		input.close();
		
		return nRead;
	}

	/**
	 * Uses the header of a service message to find the indices where the header and first line terminate.
	 * 
	 * @param packet the packet storing the header
	 * @return whether the header is a valid service message
	 */
	private boolean findHeaderIndices(DatagramPacket packet) {

		byte[] data = packet.getData();
		String msg = new String(data);
		
		// Find first header line termination and overall header terminator
		this.lineEndI = msg.indexOf("\r\n");
		this.headerEndI = msg.indexOf("\r\n\r\n");
		
        String headerPos = "found line end at " + this.lineEndI + " and header end at " + this.headerEndI;
        SystemManager.getInstance().logPrint(headerPos, SystemManager.LogLevel.VERBOSE);
		
		if(this.lineEndI < 0 || this.headerEndI < 0) {
	        String headerErr = "non-terminated message received!";
	        SystemManager.getInstance().logPrint(headerErr, SystemManager.LogLevel.DEBUG);
	        return false;
		}
		
		return true;
	}
	
	/**
	 * Extracts the header from a service message, returning it. If the header is invalid <code>null</code> is returned.
	 * 
	 * @param packet the packet to extract the header from
	 * @return the header fields, or null if the header is invalid
	 */
	public String[] stripHeader(DatagramPacket packet) {

		if(!findHeaderIndices(packet)) return null;
		
		byte[] data = packet.getData();
		String msg = new String(data);
		
		String header = msg.substring(0, this.headerEndI);
		SystemManager.getInstance().logPrint("received: " + header.replaceAll(lineTermination, " / "), SystemManager.LogLevel.SERVICE_MSG);
		
		// Separate first header line into string
		String firstHeaderLine = msg.substring(0, this.lineEndI);
		ArrayList<String> headerFields = new ArrayList<String>(Arrays.asList(firstHeaderLine.split("[ ]+")));

		// Parse second header line if current Peer is enhanced
		if(this.lineEndI != this.headerEndI && !Peer.getInstance().getProtocolVersion().equals("1.0")) {
			
			String secondHeaderLine = msg.substring(this.lineEndI, this.headerEndI);
			secondHeaderLine = secondHeaderLine.replaceAll(lineTermination, "");
			ArrayList<String> additionalFields = new ArrayList<String>(Arrays.asList(secondHeaderLine.split("[ ]+")));
			
			// Merge additional fields
			SystemManager.getInstance().logPrint("merging second header line", SystemManager.LogLevel.DEBUG);
			headerFields.addAll(additionalFields);
		}
		
		// Validate header
		if(this.headerValidation(headerFields)) return headerFields.toArray(new String[0]);
		else return null;
	}
	
	/**
	 *  Validates a service message size and header and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the header is valid
	 */
	private boolean headerValidation(ArrayList<String> fields) {
		
		if(fields.size() < minimumMsgLen) {
			SystemManager.getInstance().logPrint("invalid header, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}

		if(!validateHeader(fields.toArray(new String[0]))) return false;
		else return true;
	}
	
	/**
	 * Validates a service message header and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the header is valid
	 */
	private boolean validateHeader(String[] fields) {
		
		String protocol = fields[protocolI].toUpperCase();
		
		// Validate header size and fields for each known protocol type
		switch(protocol) {
		
		// BACKUP protocol initiator message
		case "PUTCHUNK":
			
			if(!validateHeaderSize(fields.length, putchunkMinMsgLen, "PUTCHUNK")) return false;
			if(!validatePutchunk(fields)) return false;
			return true;
			
		// BACKUP protocol response message
		case "STORED":
			
			if(!validateHeaderSize(fields.length, storedMinMsgLen, "STORED")) return false;
			if(!validateStored(fields)) return false;
			return true;
			
		// DELETE protocol initiator message
		case "DELETE":
			
			if(!validateHeaderSize(fields.length, deleteMinMsgLen, "DELETE")) return false;
			if(!validateDelete(fields)) return false;
			return true;
			
		// DELETE protocol response message for enhanced DELETE
		case "DELETED":
			
			if(!validateHeaderSize(fields.length, deletedMinMsgLen, "DELETED")) return false;
			if(!validateDeleted(fields)) return false;
			return true;
			
		// RESTORE protocol initiator message
		case "GETCHUNK":
			
			if(fields.length != enhancedGetchunkMinMsgLen) {
				if(!validateHeaderSize(fields.length, getchunkMinMsgLen, "GETCHUNK")) return false;
			}
			if(!validateGetchunk(fields)) return false;
			return true;
		
		// RESTORE protocol response message
		case "CHUNK":
			
			if(!validateHeaderSize(fields.length, chunkMinMsgLen, "CHUNK")) return false;
			if(!validateChunk(fields)) return false;
			return true;
			
		// RECLAIM protocol message
		case "REMOVED":
			
			if(!validateHeaderSize(fields.length, removedMinMsgLen, "REMOVED")) return false;
			if(!validateRemoved(fields)) return false;
			return true;
			
		// Peer started message used for DELETE protocol enhancement
		case "STARTED":
			
			if(!validateHeaderSize(fields.length, startedMinMsgLen, "STARTED")) return false;
			if(!validateStarted(fields)) return false;
			return true;
			
		// Unknown protocols
		default:
			SystemManager.getInstance().logPrint("unrecognized protocol, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
	}
	
	/**
	 * Validates a PUTCHUNK message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the PUTCHUNK message is valid
	 */
	private boolean validatePutchunk(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]) && validateChunkNo(fields[backChunkNoI]) && validateRepDeg(fields[backRepDegI]);
		
		return validate;
	}
	
	/**
	 * Validates a STORED message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the STORED message is valid
	 */
	private boolean validateStored(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]) && validateChunkNo(fields[backChunkNoI]);
		
		return validate;
	}
	
	/**
	 * Validates a DELETE message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the DELETE message is valid
	 */
	private boolean validateDelete(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]);
		
		return validate;
	}

	/**
	 * Validates a DELETED message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the DELETED message is valid
	 */
	private boolean validateDeleted(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]);
		
		return validate;
	}
	
	/**
	 * Validates a GETCHUNK message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the GETCHUNK message is valid
	 */
	private boolean validateGetchunk(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]) && validateChunkNo(fields[backChunkNoI]);

		if(validate && (fields.length == enhancedGetchunkMinMsgLen)) {
			return validateAddress(fields[addressI]);
		}
		
		return validate;
	}
	
	/**
	 * Validates a CHUNK message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the CHUNK message is valid
	 */
	private boolean validateChunk(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]) && validateChunkNo(fields[backChunkNoI]);
		
		return validate;
	}
	
	/**
	 * Validates a REMOVED message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the REMOVED message is valid
	 */
	private boolean validateRemoved(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI])
				&& validateHash(fields[hashI]) && validateChunkNo(fields[backChunkNoI]);
		
		return validate;
	}
	
	/**
	 * Validates a STARTED message and returns whether it's valid.
	 * 
	 * @param fields the header fields
	 * @return whether the STARTED message is valid
	 */
	private boolean validateStarted(String[] fields) {
		
		boolean validate = validateVersion(fields[protocolVersionI]) && validateSenderID(fields[senderI]);
		
		return validate;
	}
	
	/**
	 * Validates the number of fields in the header and returns whether it's valid.
	 * 
	 * @param length the length of the header field
	 * @param expectedLen the expected length
	 * @param protocol the protocol name
	 * @return whether the number of fields is valid
	 */
	private boolean validateHeaderSize(int length, int expectedLen, String protocol) {
		
		if(length < expectedLen) {
			SystemManager.getInstance().logPrint(expectedLen + " fields are needed for " + protocol + ", ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		if(length > expectedLen) {
			if(!ignoreMinorErrors) SystemManager.getInstance().logPrint("extra fields found for " + protocol + ", ignoring message...", SystemManager.LogLevel.DEBUG);
			else SystemManager.getInstance().logPrint("extra fields found for " + protocol + ", ignoring error...", SystemManager.LogLevel.DEBUG);
			return ignoreMinorErrors;
		}
		
		return true;
	}
	
	/**
	 * Validates the protocol version field and returns whether it's valid.
	 * 
	 * @param version the protocol version field
	 * @return whether the protocol version field is valid
	 */
	private boolean validateVersion(String version) {

		if(version.length() != expectedVersionLen) {
			if(!ignoreMinorErrors) SystemManager.getInstance().logPrint("version length doesn't match, ignoring message...", SystemManager.LogLevel.DEBUG);
			else SystemManager.getInstance().logPrint("version length doesn't match, ignoring error...", SystemManager.LogLevel.DEBUG);
			return ignoreMinorErrors;
		}
		
		if(!Character.isDigit(version.charAt(0)) || !Character.isDigit(version.charAt(2))) {
			if(!ignoreMinorErrors) SystemManager.getInstance().logPrint("version isn't in <n>.<m> format, ignoring message...", SystemManager.LogLevel.DEBUG);
			else SystemManager.getInstance().logPrint("version isn't in <n>.<m> format, ignoring error...", SystemManager.LogLevel.DEBUG);
			return ignoreMinorErrors;
		}
		
		return true;
	}

	/**
	 * Validates the sending Peer ID field and returns whether it's valid.
	 * 
	 * @param sender the sending Peer ID field
	 * @return whether the sending Peer ID field is valid
	 */
	private boolean validateSenderID(String sender) {
		
		try {
			Integer.parseInt(sender);
		} catch(NumberFormatException e) {
			SystemManager.getInstance().logPrint("peer ID isn't a number, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		return true;
	}

	/**
	 * Validates the SHA256 field and returns whether it's valid.
	 * 
	 * @param hash the SHA256 field
	 * @return whether the SHA256 field is valid
	 */
	private boolean validateHash(String hash) {
		
		if(hash.length() != expectedHashLen) {
			SystemManager.getInstance().logPrint("SHA256 doesn't have 64 chars, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		return true;
	}

	/**
	 * Validates the chunk number field and returns whether it's valid.
	 * 
	 * @param chunkNo the chunk number field
	 * @return whether the chunk number field is valid
	 */
	private boolean validateChunkNo(String chunkNo) {
		
		int value;
		try {
			value = Integer.parseInt(chunkNo);
		} catch(NumberFormatException e) {
			SystemManager.getInstance().logPrint("chunk number isn't a number, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		if(value < 0 || value > maxChunkNo) {
			SystemManager.getInstance().logPrint("chunk number outside [0, 1000000] range, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Validates the replication degree field and returns whether it's valid.
	 * 
	 * @param repDeg the replication degree field
	 * @return whether the replication degree field is valid
	 */
	private boolean validateRepDeg(String repDeg) {
		
		int value;
		try {
			value = Integer.parseInt(repDeg);
		} catch(NumberFormatException e) {
			SystemManager.getInstance().logPrint("replication degree isn't a number, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		if(value < minRepDeg || value > maxRepDeg) {
			SystemManager.getInstance().logPrint("replication degree outside [1, 9] range, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Validates the address field and returns whether it's valid.
	 * 
	 * @param address the address and port of a server
	 * @return whether the address field is valid
	 */
	private boolean validateAddress(String address) {

		String[] split = address.split(":");
		if(split.length != 2) {
			SystemManager.getInstance().logPrint("address is not in \"IP:port\" format, ignoring message...", SystemManager.LogLevel.DEBUG);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Extracts the data body from a service message, returning the underlying byte array.
	 * 
	 * @param packet the packet to extract the body from
	 * @return the body data as a byte array
	 */
	public byte[] stripBody(DatagramPacket packet) throws IOException {

		// Calculate body size and offset
		int bodySize = packet.getLength() - this.headerEndI - headerTerminationSize;
		int bodyOffset = this.headerEndI + headerTerminationSize;
		byte[] data = packet.getData();
		
		String bodySizeMsg = "packet length: " + packet.getLength() + "  body size: " + bodySize;
		SystemManager.getInstance().logPrint(bodySizeMsg, SystemManager.LogLevel.VERBOSE);
		
		// Copy body data to byte array byte setting offset to header end
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(data, bodyOffset, bodySize);
		byte[] bodyData = output.toByteArray();

		output.close();
		
		return bodyData;
	}
	
}