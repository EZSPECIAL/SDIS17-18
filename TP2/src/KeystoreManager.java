import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Scanner;

public class KeystoreManager {

	// TODO doc
	public void accessKeystore() throws IOException {
		
		KeyStore ks;
		try {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
		} catch(KeyStoreException e) {
			SystemManager.getInstance().logPrint("key store exception!", SystemManager.LogLevel.NORMAL);
			e.printStackTrace();
			return;
		}
		
		this.createKeystore(ks);
		
//	    java.io.FileInputStream fis = null;
//	    try {
//	        fis = new java.io.FileInputStream("keyStoreName");
//	        ks.load(fis, password);
//	    } finally {
//	        if (fis != null) {
//	            fis.close();
//	        }
//	    }
	}
	
	// TODO doc
	private void createKeystore(KeyStore ks) throws IOException {
		
	    // get user password and file input stream
		System.out.println("Input keystore password:");
	    Scanner scanner = new Scanner(System.in);
	    String inputString = scanner.nextLine();
	    
	    File file = new File("../keystore"); // TODO hardcoded
	    FileOutputStream out = new FileOutputStream(file);
	    
	    try {
	    	ks.load(null, inputString.toCharArray());
			ks.store(out, inputString.toCharArray());
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CertificateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//	    try {
//			ks.load(null, inputString.toCharArray());
//		} catch (NoSuchAlgorithmException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (CertificateException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
}