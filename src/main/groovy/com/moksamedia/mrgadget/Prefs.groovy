package com.moksamedia.mrgadget

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
	
	public Prefs(def params = [:]) {
		
		String val = params.get('val', null)
		boolean clearAllPasswords = params.get('clearAllPasswords', false)
		
		prefs = Preferences.userNodeForPackage(this.class)
		stringEncryptor = new StandardPBEStringEncryptor()
				
		if (clearAllPasswords) resetPrefs()
	
		boolean isFirstLoad = isFirstLoad() // this must go AFTER resetPrefs()
		
		// if we're passing in a password, use that
		if (val != null) {
			stringEncryptor.setPassword(val)
		}
		// otherwise, use a generated one
		else {
			
			// generate the pass on first load (or after reset)
			if (isFirstLoad) {
				def v = gvls(seedStringLength) // should be length of char strings
				def ks = seedStrings.inject([]) { a, vl ->
					a += [vl+'1', vl+'2']; a
				}
				ks.eachWithIndex { it, i ->
					prefs.putInt((it), v[i])
				}
				prefs.flush()
			}
			
			// load and reconstruct password			
			int i = 0
			stringEncryptor.setPassword( seedStrings.inject("") { a2, v2 -> 
				a2 += (this."$v2")[(prefs.getInt("${v2}1", -1))..(prefs.getInt("${v2}2", -1))]
				i += 2
				a2 })	
	
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
		
		if (!prefs.getBoolean("PROGRAM_RUN", false)) { // negate here, think of what we're actually storing as "has been run"
			log.info "IS FIRST LOAD"
			prefs.putBoolean("PROGRAM_RUN", true)
			true
		}
		else {
			log.info "IS NOT FIRST LOAD"
			false
		}
	}

	protected void resetPrefs() {
		log.info "Resetting prefs and removing all passwords"
		prefs.removeNode()
		prefs = Preferences.userNodeForPackage(this.class)
	}
	
	// RANDOM PASSWORD GENERATION STUFF
	def seedStringLength = 40
	def seedStrings = 'abcd'
	String a = "a234h1onnj20938hwoiejf092u3hsidbfn23240u2" // length = 41 chars
	String b = "2093bkejr20023984ksjnviuwh897y239hwu93u4o"
	String c = "20w8ehs20398uonlaw84u52j3hknqlnbvjhouhsjd"
	String d = "sdjf2083yu4r2038uwhjfnwy8293honfd238u8owj"
		
	Random rand = new Random()
	
	Closure rr = { int aStart, int aEnd ->
		long range = (long)aEnd - (long)aStart + 1
		long fraction = (long)(range * rand.nextDouble())
		(int)(fraction + aStart)
	}
	
	Closure gnp = { int min, int max ->
		int half = Math.floor(max - min) / 2
		[rr(min,half), rr(half,max)]
	}
	
	Closure gvls = { int len ->
		gnp(0,len) + gnp(0,len) + gnp(0,len) + gnp(0,len)
	}

}
