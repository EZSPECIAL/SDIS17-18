import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeystoreManager {

	private char[] pw;
	
	/**
	 * Constructs a KeystoreManager responsible for managing the
	 * secret keys needed for MAC and encryption of service messages.
	 * 
	 * @param pw the password for the keystore
	 */
	public KeystoreManager(String pw) {
		this.pw = pw.toCharArray();
	}

	/**
	 * Verifies if keystore exists, if not creates one with a
	 * secret key for AES-128 and another for HMAC SHA256.
	 */
	public void verifyKeystore() throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableEntryException {
		
		KeyStore ks = KeyStore.getInstance("PKCS12");
		
		// Create KeyStore and secret key if needed
		if(!this.checkForKeystore()) {
			this.createKeystore(ks);
			this.saveKeystore(ks);
		}
	}
	
	/**
	 * Uses the specified alias to retrieve a secret key from the keystore.
	 * 
	 * @param alias the secret key alias
	 * @return the secret key
	 */
	public SecretKey getKey(String alias) throws IOException, NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, CertificateException {
		
		KeyStore ks = KeyStore.getInstance("PKCS12");
		this.loadKeystore(ks);
		
		KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(this.pw);
		KeyStore.SecretKeyEntry skEntry = (KeyStore.SecretKeyEntry) ks.getEntry(alias, protParam);
		return skEntry.getSecretKey();
	}
	
	/**
	 * Checks the expected keystore path to see if keystore already exists.
	 * 
	 * @return whether the keystore exists
	 */
	private boolean checkForKeystore() {
		
		File file = new File("../" + Peer.keystoreName);
		return file.exists();
	}

	/**
	 * Creates a keystore with a AES-128 secret key and a
	 * HMAC SHA256 secret key.
	 * 
	 * @param ks the keystore to use
	 */
	private void createKeystore(KeyStore ks) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException {
		
	    ks.load(null, this.pw);

	    // Insert AES-128 and HMAC SHA256 secret keys into KeyStore
	    KeyGenerator encryptKey = KeyGenerator.getInstance("AES");
	    KeyGenerator macKey = KeyGenerator.getInstance("HmacSHA256");
	    encryptKey.init(SecurityHandler.encryptSizeBit);
	    macKey.init(SecurityHandler.macSizeBit);
	    
	    SecretKey encryptSk = encryptKey.generateKey();
	    SecretKey macSk = macKey.generateKey();
	    
	    KeyStore.SecretKeyEntry encryptEntry = new KeyStore.SecretKeyEntry(encryptSk);
	    KeyStore.SecretKeyEntry macEntry = new KeyStore.SecretKeyEntry(macSk);
	    KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(this.pw);
	    ks.setEntry(Peer.encryptAlias, encryptEntry, protParam);
	    ks.setEntry(Peer.macAlias, macEntry, protParam);
	}
	
	/**
	 * Loads a keystore from file.
	 * 
	 * @param ks the keystore to use
	 */
	private void loadKeystore(KeyStore ks) throws IOException, NoSuchAlgorithmException, CertificateException {
		
	    File file = new File("../" + Peer.keystoreName);
	    FileInputStream in = new FileInputStream(file);
	    
	    ks.load(in, this.pw);
	}

	/**
	 * Saves a keystore to file.
	 * 
	 * @param ks the keystore to use
	 */
	private void saveKeystore(KeyStore ks) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException{
		
	    File file = new File("../" + Peer.keystoreName);
	    FileOutputStream out = new FileOutputStream(file);
	    
	    ks.store(out, this.pw);
	}
}