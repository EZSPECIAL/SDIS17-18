import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServiceMessage {

	private static final String lineTermination = "\r\n";
	private static final String headerTermination = "\r\n\r\n";
	private static final int headerTerminationSize = 4;
	private static final int dataSize = 64000;
	
	// Message header termination indices
	private int lineEndI = 0;
	private int headerEndI = 0;

	// PUTCHUNK <Version> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
	// TODO document
	public byte[] putChunk(String version, int senderID, String fileID, int chunkNo, int replicationDeg) {
		
		String msg = "PUTCHUNK " + version + " " + senderID + " " + fileID + " " + chunkNo + " " + replicationDeg + headerTermination;
		
		byte[] header = msg.getBytes();
		
	    Path path = Paths.get(fileID);
	    byte[] buf = new byte[dataSize];
	    
	    int nRead = 0;
	    try {
		    InputStream input = Files.newInputStream(path); // TODO change to FileInputStream, add file exists check
			nRead = input.read(buf, 0, dataSize); // TODO divide file
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    byte[] finalMsg = new byte[nRead];
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(header, 0, header.length);
	    output.write(buf, 0, nRead);
	    finalMsg = output.toByteArray();
	    
		try {
			output.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//System.out.println("nread: " + nRead);
		
	    // TODO SHA256 of fileID
	    return finalMsg;
	}
	
	// TODO document
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
	
	// TODO document
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
	
	// TODO document
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return bodyData;
	}
	
}