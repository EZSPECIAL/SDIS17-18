import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;

public class SecurityHandler {

	public static final int macSizeBit = 256;
	public static final int macSizeByte = 32;
	public static final int encryptSizeBit = 128;
	public static final int encryptSizeByte = 16;
	
	/**
	 * Computes a MAC using SHA256 and returns it in textual hex representation.
	 * 
	 * @param data the binary data to use for MAC computation
	 * @return the computed MAC
	 */
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
	
	/**
	 * Encrypts binary data using AES-128 algorithm.
	 * 
	 * @param data the data to encrypt
	 * @return the encrypted data
	 */
	public static byte[] encryptAES128(byte[] data) throws IOException {
		
		SecretKey sk = null;
		try {
			sk = Peer.getInstance().getKsManager().getKey(Peer.encryptAlias);
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("AES-128 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		} catch(CertificateException | KeyStoreException | UnrecoverableEntryException e) {
			SystemManager.getInstance().logPrint("Exception retrieving key from keystore!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("AES-128 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		} catch(NoSuchPaddingException e) {
			SystemManager.getInstance().logPrint("PKCS5 padding not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			cipher.init(Cipher.ENCRYPT_MODE, sk);
		} catch (InvalidKeyException e) {
			SystemManager.getInstance().logPrint("Invalid key for AES-128!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			return cipher.doFinal(data);
		} catch(IllegalBlockSizeException | BadPaddingException e) {
			SystemManager.getInstance().logPrint("Exception running cipher!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		return data;
	}
	
	/**
	 * Decrypts binary data using AES-128 algorithm.
	 * 
	 * @param data the data to decrypt
	 * @return the decrypted data
	 */
	public static byte[] decryptAES128(byte[] data) throws IOException {
		
		SecretKey sk = null;
		try {
			sk = Peer.getInstance().getKsManager().getKey(Peer.encryptAlias);
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("AES-128 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		} catch(CertificateException | KeyStoreException | UnrecoverableEntryException e) {
			SystemManager.getInstance().logPrint("Exception retrieving key from keystore!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		} catch(NoSuchAlgorithmException e) {
			SystemManager.getInstance().logPrint("AES-128 algorithm not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		} catch(NoSuchPaddingException e) {
			SystemManager.getInstance().logPrint("PKCS5 padding not found!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			cipher.init(Cipher.DECRYPT_MODE, sk);
		} catch (InvalidKeyException e) {
			SystemManager.getInstance().logPrint("Invalid key for AES-128!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			return cipher.doFinal(data);
		} catch(IllegalBlockSizeException | BadPaddingException e) {
			SystemManager.getInstance().logPrint("Exception running cipher!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			System.exit(-1);
		}
		
		return data;
	}
}
