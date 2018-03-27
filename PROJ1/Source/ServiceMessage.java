public class ServiceMessage {

	private String message;
	
	// PUTCHUNK <Version> <SenderId> <FileId> <ChunkNo> <ReplicationDeg> <CRLF><CRLF><Body>
	public void putChunk(String version, String senderID, String fileID, String chunkNo, String replicationDeg) {
		
		String msg = "PUTCHUNK ";
		msg += version + " " + senderID + " " + fileID + " " + chunkNo + " " + replicationDeg + "\r\n\r\n";
		
		this.message = msg;
	}
	
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}