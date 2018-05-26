import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;

public class SecurityHandler {

	public static final int macSizeBit = 256;
	public static final int macSizeByte = 32;
	public static final int encryptSizeBit = 256;
	public static final int encryptSizeByte = 32;
	
	// TODO doc
	public static String computeMAC(byte[] data) throws IOException {
		
		Mac sha256_HMAC = null;
		try {
			sha256_HMAC = Mac.getInstance("HmacSHA256");
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("HMAC SHA256 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		SecretKey sk = null;
		try {
			sk = Peer.getInstance().getKsManager().getKey(Peer.macAlias);
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("HMAC SHA256 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		} catch(CertificateException | KeyStoreException | UnrecoverableEntryException e) {
			SystemManager.getInstance().logPrint("Exception retrieving key from keystore!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			sha256_HMAC.init(sk);
		} catch (InvalidKeyException e) {
			SystemManager.getInstance().logPrint("Invalid key for HMAC SHA256!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}

		// Finish MAC operation and return the array
		byte[] mac = sha256_HMAC.doFinal(data);
		String hex = DatatypeConverter.printHexBinary(mac);
		SystemManager.getInstance().logPrint("Computed MAC: " + hex, SystemManager.LogLevel.VERBOSE);
		return hex;
	}
}
