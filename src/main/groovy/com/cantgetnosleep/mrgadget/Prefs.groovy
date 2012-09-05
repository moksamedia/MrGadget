package com.cantgetnosleep.mrgadget

import groovy.util.logging.Slf4j

import java.security.KeyStore
import java.util.prefs.Preferences
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor

/**
 *
 * @author ach857
 */

@Slf4j
class Prefs {

	Preferences prefs
	KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

	StandardPBEStringEncryptor stringEncryptor


	String a = "a234h1onnj20938hwoiejf092u3hsidbfn23240u2" // length = 41 chars
	String b = "2093bkejr20023984ksjnviuwh897y239hwu93u4o"
	String c = "20w8ehs20398uonlaw84u52j3hknqlnbvjhouhsjd"
	String d = "sdjf2083yu4r2038uwhjfnwy8293honfd238u8owj"
	
	public Prefs() {
		
		prefs = Preferences.userNodeForPackage(this.class)

		stringEncryptor = new StandardPBEStringEncryptor()
				
		stringEncryptor.setPassword(a[0..10] + b[5..12] + c + d[3..8])
		stringEncryptor.initialize()
	}
	
	protected String getSudoPassword(String user, String host) {
		getPassword(user, host+"SUDO")
	}

	protected void storeSudoPassword(String pass, String user, String host) {
		storePassword(pass, user, host+"SUDO")
	}
	
	protected String getPassword(String user, String host) {

		Preferences userNode = prefs.node(user)
		
		String encPass = userNode.get(host, null)
		
		if (encPass == null)
		{
			return null;
		}
		else {
			return stringEncryptor.decrypt(encPass)
		}

	}

	protected void storePassword(String pass, String user, String host) {
		Preferences userNode = prefs.node(user)
		String encPass = stringEncryptor.encrypt(pass)
		userNode.put(host, encPass)
		prefs.flush()
	}

	protected void removeAllPrefsForUser(String user) {
		Preferences userNode = prefs.node(user)
		userNode.removeNode()
		prefs.flush()
	}

	protected void removeAllPrefsForUserAtHost(String user, String host) {
		Preferences userNode = prefs.node(user)
		userNode.remove(host)
		userNode.remove(host+"SUDO")
		prefs.flush()
	}

	protected boolean isFirstLoad() {
		return prefs.getBoolean("PROGRAM_RUN", false)
	}

	protected void onFirstLoad() {
		prefs.putBoolean("PROGRAM_RUN", true)
	}

	protected void resetPrefs() {
		prefs.removeNode()
		prefs.flush()
		prefs = Preferences.userNodeForPackage(this.class)
	}
}
