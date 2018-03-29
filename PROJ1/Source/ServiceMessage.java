import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;

public class ServiceMessage {

	// Useful constants
	private static final String lineTermination = "\r\n";
	private static final String headerTermination = "\r\n\r\n";
	private static final int headerTerminationSize = 4;
	private static final int dataSize = 64000;
	
	// Message header termination indices
	private int lineEndI = 0;
	private int headerEndI = 0;

	// PUTCHUNK <Version> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
	// DOC document
	public byte[] putChunk(int peerID, ProtocolState state) throws IOException {

		// Get binary file data
	    byte[] buf = new byte[dataSize];
		int nRead = this.getData(state.getFilepath(), buf);
		
        String readMsg = "putchunk nRead: " + nRead;
        SystemManager.getInstance().logPrint(readMsg, SystemManager.LogLevel.VERBOSE);
	    
	    // Merge header and body to single byte[]
		String header = "PUTCHUNK " + state.getProtocolVersion() + " " + peerID + " " + state.getHashHex() + " " + state.getCurrentChunkNo() + " " + state.getDesiredRepDeg() + headerTermination;
		return this.mergeByte(header.getBytes(), header.getBytes().length, buf, nRead);

	    // TODO check nRead for file end
		// TODO file already backed up by this Peer
		// TODO chunk no handling with ProtocolState
		// TODO chunkNo up to 6 digits (max 64GB)
		// TODO chunks multiple of 64KB
	}
	
	// DOC document
	private byte[] mergeByte(byte[] arr1, int arr1Len, byte[] arr2, int arr2Len) throws IOException {
		
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(arr1, 0, arr1Len);
	    output.write(arr2, 0, arr2Len);
	    byte[] merged = output.toByteArray();
	    output.close();
	    
	    return merged;
	}
	
	// DOC document
	private int getData(String filepath, byte[] data) throws IOException {

		FileInputStream input = new FileInputStream(filepath);
		int nRead = input.read(data, 0, dataSize);
		input.close();
		
		return nRead;
	}

	// DOC document
	public boolean findHeaderIndices(DatagramPacket packet) {

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
	
	// DOC document
	public String[] stripHeader(DatagramPacket packet) {

		byte[] data = packet.getData();
		String msg = new String(data);
		
		// Separate header into string
		String header = msg.substring(0, this.lineEndI);
		SystemManager.getInstance().logPrint(header, SystemManager.LogLevel.DEBUG);
		
		// Divide by fields
		String[] headerFields = header.split("[ ]");
		
		// TODO validate header
		
		return headerFields;
	}
	
	// DOC document
	public byte[] stripBody(DatagramPacket packet) {

		// Calculate body size and offset
		int bodySize = packet.getLength() - this.headerEndI - headerTerminationSize;
		int bodyOffset = this.headerEndI + headerTerminationSize;
		byte[] data = packet.getData();
		
		String bodySizeMsg = "body size: " + bodySize;
		SystemManager.getInstance().logPrint(bodySizeMsg, SystemManager.LogLevel.VERBOSE);
		
		// Copy body data to byte array byte setting offset to header end
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(data, bodyOffset, bodySize);
		byte[] bodyData = output.toByteArray();

		try {
			output.close();
		} catch (IOException e) {
			// CATCH Auto-generated catch block
			e.printStackTrace();
		}
		
		return bodyData;
	}
	
}