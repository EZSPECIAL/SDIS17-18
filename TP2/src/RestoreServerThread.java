import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.Socket;

public class RestoreServerThread implements Runnable {
	
	private Socket client;
	
	/**
	 * RESTORE ENH server thread for receiving client data.
	 * 
	 * @param client the socket to which the client is connected
	 */
	public RestoreServerThread(Socket client) {
		this.client = client;
	}

	/**
	 * Receives a CHUNK message through TCP and passes it along to
	 * the matching RESTORE protocol.
	 */
	private void receiveAndProcessChunk() throws IOException, ClassNotFoundException {

		ObjectInputStream input = new ObjectInputStream(client.getInputStream());

		// Wait for input to be available
		while(input.available() < 0);
		RestorePayload payload = (RestorePayload) input.readObject();
		DatagramPacket packet = new DatagramPacket(payload.getData(), payload.getLength());

		input.close();
		client.close();
		
		// Validate message as service message and extract its header
		ProtocolState state = new ProtocolState(new ServiceMessage());
		Peer peer = Peer.getInstance();
		state.setPacket(packet);
		state.setFields(state.getParser().stripHeader(state.getPacket()));
		
		// Message was not recognised, ignore
		if(state.getFields() == null) return;
		
		// Validate MAC
		if(!state.getParser().validateMAC(state.getPacket())) return;
		
		// Check if this RESTORE protocol exists
		String protocolKey = peer.getPeerID() + state.getFields()[Peer.hashI] + ProtocolState.ProtocolType.RESTORE.name();
		ProtocolState currState = peer.getProtocols().get(protocolKey);
	    
		if(currState == null) {
			SystemManager.getInstance().logPrint("received CHUNK through TCP but no RESTORE protocol matched, key: " + protocolKey, SystemManager.LogLevel.DEBUG);
			return;
		}
		
		// Store chunk number and chunk data received
		Long chunkNo = Long.parseLong(state.getFields()[Peer.chunkNoI]);
		byte[] data = SecurityHandler.decryptAES128(state.getParser().stripBody(state.getPacket()));
		
		SystemManager.getInstance().logPrint("restored chunk \"" + state.getFields()[Peer.hashI] + "." + chunkNo + "\"", SystemManager.LogLevel.DEBUG);
		currState.getRestoredChunks().put(chunkNo, data);
	}
	
	@Override
	public void run() {
		
		Thread.currentThread().setName("TCP Thread " + Thread.currentThread().getId());
		
		try {
			this.receiveAndProcessChunk();
		} catch (IOException e) {
			SystemManager.getInstance().logPrint("I/O Exception receiving from client!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		} catch (ClassNotFoundException e) {
			SystemManager.getInstance().logPrint("Class not found receiving object from client!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
	}
}
