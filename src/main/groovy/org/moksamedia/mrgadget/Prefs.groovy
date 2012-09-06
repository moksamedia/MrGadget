package org.moksamedia.mrgadget

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
	
	public Prefs(String val = null, boolean reset = false) {
		
		prefs = Preferences.userNodeForPackage(this.class)
		stringEncryptor = new StandardPBEStringEncryptor()
		
		if (reset) resetPrefs()
		
		// if we're passing in a password, use that
		if (val != null) {
			stringEncryptor.setPassword(val)
		}
		// otherwise, use a generated one
		else {
			// generate the pass on first load (or after reset)
			if (isFirstLoad()) {
				def v = genVals(40) // should be length of char strings
				prefs.put('a1', v[0][0])
				prefs.put('a2', v[0][1])
				prefs.put('b1', v[1][0])
				prefs.put('b2', v[1][1])
				prefs.put('c1', v[2][0])
				prefs.put('c2', v[2][1])
				prefs.put('d1', v[3][0])
				prefs.put('d2', v[3][1])
				prefs.flush()
			}
			// get the generated password
			stringEncryptor.setPassword(
				prefs.get('a1','0') + prefs.get('a2','0') +
				prefs.get('b1','0') + prefs.get('b2','0') +
				prefs.get('c1','0') + prefs.get('c2','0') +
				prefs.get('d1','0') + prefs.get('d2','0'))
			
		}
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
		prefs.getBoolean("PROGRAM_RUN", false)
	}

	protected void onFirstLoad() {
		prefs.putBoolean("PROGRAM_RUN", true)
	}

	protected void resetPrefs() {
		log.info "Resetting prefs and removing all passwords"
		prefs.removeNode()
		prefs.flush()
		prefs = Preferences.userNodeForPackage(this.class)
	}
	
	// RANDOM PASSWORD GENERATION STUFF
	
	String a = "a234h1onnj20938hwoiejf092u3hsidbfn23240u2" // length = 41 chars
	String b = "2093bkejr20023984ksjnviuwh897y239hwu93u4o"
	String c = "20w8ehs20398uonlaw84u52j3hknqlnbvjhouhsjd"
	String d = "sdjf2083yu4r2038uwhjfnwy8293honfd238u8owj"
		
	Random rand = new Random()
	
	Closure randRange = { int aStart, int aEnd ->
		long range = (long)aEnd - (long)aStart + 1
		long fraction = (long)(range * rand.nextDouble())
		(int)(fraction + aStart)
	}
	
	Closure getNumPair = { int min, int max ->
		int half = Math.floor(max - min) / 2
		[randRange(min,half), randRange(half,max)]
	}
	
	Closure genVals = { int len ->
		[getNumPair(0,len), getNumPair(0,len), getNumPair(0,len), getNumPair(0,len)]
	}

}
