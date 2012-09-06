package com.moksamedia.mrgadget

import groovy.util.logging.Slf4j

import java.awt.BorderLayout
import java.awt.Font
import java.text.DecimalFormat

import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.border.EmptyBorder

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import com.jcraft.jsch.UserInfo


/**
 * MrGadget offers four services:
 * 1) SCP (secure copy a file via SSH) to a remote server
 * 2) SFTP a file to a remote server
 * 3) execute a non-sudo command on a remote server
 * 4) execute a sudo command on a remote server
 * 
 * A user and host must be specified before any operation can be performed. This can be set
 * via the constructor, or by accessing the MrGadget instance directly, and can be changed
 * between operations (but not if leave session open is set to true, as the new values will
 * be ignored).
 * 
 * Passwords can be saved to the system's user preferences. Passwords are saved using reasonable
 * encryption, and the encryption key has obfuscated (a little), but NO GUARANTEE is made
 * regarding the security of this. It is certainly safer than storing it in plain text in a 
 * config for or a gradle build file, but an interested hacker could reverse engineer
 * the key and discover you password, so USE AT YOUR OWN RISK.
 * 
 * @author Andrew Hughes, aka cantgetnosleep
 *
 */
class MrGadget {

	final Logger log = LoggerFactory.getLogger("MrGadget")
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// MAIN OBJECTS AND VALUES

	String host
	String user
	Session session
	
	JSch jsch = new JSch()
	Prefs prefs
	UserInfo ui = new MyUserInfo()
	
	DecimalFormat decFormat
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// OPTIONS
	
	// leave the session open after completing task (may want to do this if we're 
	// executing multiple actions)
	boolean leaveSessionOpen = false
	
	// set to true if the SUDO password is different than the user's host login password
	boolean sudoPassDifferent = false
	
	// set to false if you don't want MrGadget to ask about saving the password to prefs
	boolean promptToSavePass = true
	
	// allows override of SSH strict host key checking
	boolean strictHostKeyChecking = true
	
	// copy file to remote host options
	
	// show the Swing progress bar dialog box
	boolean showProgressDialog = true
	
	// preserve modification times for copied file
	boolean preserveTimestamp = false
	
	// sets how often copy progress is logged
	int logProgressGranularity = 10
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTOR
	
	public MrGadget(def params = [:]) {
		
		// set host and user, if supplied
		this.host = params.host
		this.user = params.user
		
		// set params if specified, otherwise default to values above
		
		this.leaveSessionOpen = params.get('leaveSessionOpen', this.leaveSessionOpen)
		this.sudoPassDifferent = params.get('sudoPassDifferent', this.sudoPassDifferent)
		this.promptToSavePass = params.get('promptToSavePass', this.promptToSavePass)
		this.strictHostKeyChecking = params.get('strictHostKeyChecking', this.strictHostKeyChecking)
		// copy to remote options
		this.showProgressDialog = params.get('showProgressDialog', this.showProgressDialog)
		this.preserveTimestamp = params.get('preserveTimestamp', this.preserveTimestamp)
		this.logProgressGranularity = params.get('logProgressGranularity', this.logProgressGranularity)
		boolean clearAllPasswords = params.get('clearAllPasswords', false)
		
		if (params.containsKey("prefsEncryptionPassword")) {
			prefs = new Prefs(params.prefsEncryptionPassword,clearAllPasswords) // use passed-in password
		}
		else {
			prefs = new Prefs(null,clearAllPasswords) // null: uses generated pass or loads it
		}
		
		// decimal format
		decFormat = new DecimalFormat("#,##0.00")
		
		
	}

	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CREATE SESSION
	// - this is used by all three methods to create a (potentially common) session object
	// - leaveSessionOpen can be set to true to avoid having to reconnect to host while executing
	//   a sequence of actions
	// - if leaveSessionOpen is set to true, then the session should be closed upon completion
	
	
	// create a session and connect to host
	Closure createSession = {
		
		// don't connect if we already have a session
		if (session?.isConnected()) return
		
		session = jsch.getSession(user, host, 22);

		if (strictHostKeyChecking) {
			session.setConfig("StrictHostKeyChecking", "yes");
		}
		else {
			session.setConfig("StrictHostKeyChecking", "no");
		}

		session.setUserInfo(ui);
		
		// try and get saved password
		String pass = prefs.getPassword(user, host)
		
		// if we have a saved password
		if (pass != null) {
			log.info "Using saved password for ${user}@${host}"
			session.setPassword(pass)
			ui.passwd = pass // put the password in the ui so that if we're running a sudo command we can get it out later
			session.connect()
		}
		// if we don't have a saved password
		else {
			session.connect()
			// prompt to save, if should
			if (promptToSavePass && ui.promptYesNo("Would you like to save the password (encrypted) for ${user}@${host} in the system prefs?")) {
				prefs.storePassword(ui.getPassword(), user, host)
				log.info "Password saved (encrypted) for ${user}@${host}"
			}

		}

	}
	
	// close the session (only needed if leaveSessionOpen = true)
	public void closeSession() {
		if (session?.isConnected()) session.disconnect()
	}
	

	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CHECK USER AND HOST
	
	// make sure both host and user have been specified
	Closure checkHostAndUser = {
		if (!host) {
			log.error "Error: no host specified!"
			throw new RuntimeException("Error: no host specified!")
		}
		if (!user) {
			log.error "Error: no user specified!"
			throw new RuntimeException("Error: no user specified!")
		}
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// HANDLE EXCEPTIONS

	// common exception handling logic, with special check for host key rejection
	Closure handleException = { Exception e ->
		if (e.getMessage()?.contains('reject HostKey')) {
			log.error("Error attempting to execute command: " + e.getMessage())
			log.error "You can bypass this by setting 'strictHostKeyChecking' to false. Just be sure you understand the security risk this creates."
		}
		else {
			log.error("Error attempting to execute command!", e)
		}
		throw new RuntimeException("Error attempting to execute sudo command!", e)
	}
		
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CLEAR PASSWORDS
	
	// clear all passwords saved in prefs
	public void clearAllPasswords() {
		prefs.resetPrefs()
	}
	
	// clear password for user and host saved in prefs
	public void clearPasswordsForUserAtHost(String user, String host) {
		prefs.removeAllPrefsForUserAtHost(user, host)
	}

	// clear all passwords for user at all hosts
	public void clearPasswordsForUser(String user) {
		prefs.removeAllPrefsForUser(user)
	}

	
	public boolean copyToRemoteSFTP(def params = [:]) {
		
		checkHostAndUser()
		
		// reality check: we need a local file and a remote file
		if (!params.containsKey('localFile') || !params.containsKey('remoteFile')) {
			log.error("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			throw new RuntimeException("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			return false
		}
		
		// FILES
		
		String localFilePath = params.localFile
		String remoteFilePath = params.remoteFile
		
		// should we show the Swing progress dialog
		boolean showProgressDialog = params.get('showProgressDialog', this.showProgressDialog)
		// how often should the copy progress be logged (in integer percentage)
		int logProgressGranularity = params.get('logProgressGranularity', this.logProgressGranularity)
		
		log.info "Copying local file '$localFilePath' to ${user}@${host}:'$remoteFilePath'"
		log.info "Control-C will cancel."
				
		File localFile = new File(localFilePath)
		
		// make sure file exists
		if (!localFile.exists()) {
			log.error("Error: Unable to open local file '$localFilePath'")
			throw new RuntimeException("Unable to open local file '$localFilePath'")
			return false
		}
		
		long fSize = localFile.length()
		
		try {

			createSession()

			Channel channel = session.openChannel("sftp")
			channel.connect()
			ChannelSftp sftpChannel = (ChannelSftp) channel

			// build progress monitor for logging and dialog box
			def progMonParams = [
						host:host,
						showProgressDialog:showProgressDialog,
						bytesToSend:fSize,
						sourceFile:localFilePath,
						destinationFile:remoteFilePath,
						countClosureFrequency:logProgressGranularity
					]

			MrGadgetProgressMonitor progMon = new MrGadgetProgressMonitor(progMonParams)
			progMon.showDialog()

			log.info "Sending ${MrGadget.humanReadableByteCount(fSize)} bytes."

			sftpChannel.put(localFilePath, remoteFilePath, progMon)

			// progMon.end() is called by sftpChannel
			
			log.info "Finished sending file!"

			sftpChannel.exit()

			if (!leaveSessionOpen) session.disconnect()
			true

		}
		catch (JSchException e) {
			log.error("Unable to send file: $localFilePath.", e)
			false
		} 
		catch (SftpException e) {
			log.error("Unable to send file: $localFilePath.", e)
			false
		}
		
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// SCP
	
	/**
	 * Sends a file to a remote server using SSH scp
	 * Params are:
	 * - localFile: the full path of the local file to send
	 * - remoteFile: the full path of the file to create on the remote server (including the file name and extension)
	 * - showProgressDialog: show the Swing dialog with the progress bar (closing the dialog does NOT abort the operation)
	 * - preserveTimestamp: file copied to remote destination has same timestamp as local file
	 * - logProgressGranularity: integer percentage; progress is logged according to this size (10 = every 10%, 100 = start and finish)
	 * @return true if success
	 */
	public boolean copyToRemote(def params = [:]) {

		checkHostAndUser()

		// reality check: we need a local file and a remote file
		if (!params.containsKey('localFile') || !params.containsKey('remoteFile')) {
			log.error("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			throw new RuntimeException("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			return false
		}
		
		// FILES
		
		String localFilePath = params.localFile
		String remoteFilePath = params.remoteFile
		
		// OPTIONS
		
		// should we show the Swing progress dialog
		boolean showProgressDialog = params.get('showProgressDialog', this.showProgressDialog)
		// should the file's timestamp be preserved
		boolean preserveTimestamp = params.get('preserveTimestamp', this.preserveTimestamp)
		// how often should the copy progress be logged (in integer percentage)
		int logProgressGranularity = params.get('logProgressGranularity', this.logProgressGranularity)
		
		log.info "Copying local file '$localFilePath' to ${user}@${host}:'$remoteFilePath'"
		log.info "Control-C will cancel."
		
		FileInputStream fis
			
		// Checks the results / exit code of last executed command by reading the input stream
		Closure checkResult = { InputStream input ->
			
			int b = input.read()
			// b may be 0 for success,
			//          1 for error,
			//          2 for fatal error,
			//          -1
			if (b==0) return b 
			if (b==-1) return b 

			if(b==1 || b==2)
			{
				StringBuffer sb = new StringBuffer()	
				int c
				
				while (true) {
					c=input.read()
					sb.append((char)c)
					if (c=='\n') break
				}
				
				if(b==1) { // error
					log.error("Error: " + sb.toString())
					throw new RuntimeException("Error: " + sb.toString())
				}
				if(b==2) { // fatal error
					log.error("Fatal Error: " + sb.toString())
					throw new RuntimeException("Fatal Error: " + sb.toString())
				}
			}
			return b
		}
		
		try {

			// create a JSch session, if necessary
			createSession()
			
			// set the command and open the channel
			String command = "scp " + (preserveTimestamp ? "-p" :"") +" -t "+remoteFilePath;
			Channel channel = session.openChannel("exec");
			((ChannelExec)channel).setCommand(command);
			
			log.debug command

			// get I/O streams for remote scp
			OutputStream out = channel.getOutputStream();
			InputStream input = channel.getInputStream();

			// connect to remote server
			channel.connect()

			if(checkResult(input)!=0){
				log.error("Error opening secure copy to $host. Command=$command")
				throw new RuntimeException("Error opening secure copy to $host. Command=$command")
				return false
			}

			// get the file object
			File localFile = new File(localFilePath)
			
			// make sure file exists
			if (!localFile.exists()) {
				log.error("Error: Unable to open local file '$localFilePath'")
				throw new RuntimeException("Unable to open local file '$localFilePath'")
				return false
			}
			

			if(preserveTimestamp) {
				
				command = "T "+(localFile.lastModified()/1000)+" 0"
				
				// The access time should be sent here, but it is not accessible with JavaAPI ;-<
				
				command += (" "+(localFile.lastModified()/1000)+" 0\n")
				
				log.debug command
				
				out.write(command.getBytes())
				out.flush()
				
				if(checkResult(input)!=0) {
					log.error("Error while trying to set remote file modified and access times. Command=$command")
					throw new RuntimeException("Error while trying to set remote file modified and access times. Command=$command")
				}
			}

			// send "C0644 filesize filename", where filename should not include '/'
			long filesize=localFile.length()
			command = "C0644 "+filesize+" "
			if(localFilePath.lastIndexOf('/')>0) {
				command += localFilePath.substring(localFilePath.lastIndexOf('/')+1)
			}
			else{
				command += localFilePath
			}
			
			log.debug command
			
			command += "\n"
						
			out.write(command.getBytes())
			out.flush()
			
			if (checkResult(input)!=0){
				log.error("Error sending file copy mode, size, and name to $host. Command=$command")
				throw new RuntimeException("Error sending file copy mode, size, and name to $host. Command=$command")
				return false
			}

							
			fis = new FileInputStream(localFilePath)
			byte[] buf=new byte[2048]
			
			
			def progMonParams = [
					host:host,
					showProgressDialog:showProgressDialog,
					bytesToSend:filesize,
					sourceFile:localFilePath,
					destinationFile:remoteFilePath,
					countClosureFrequency:logProgressGranularity
				]
			
			MrGadgetProgressMonitor progMon = new MrGadgetProgressMonitor(progMonParams)
			progMon.showDialog()
						
			log.info "Sending ${MrGadget.humanReadableByteCount(filesize)} bytes."
			
			while(true) {
				
				// read from input buffer (localFile)
				int len=fis.read(buf, 0, buf.length);
				
				// if we're finished, stop
				if(len<=0) break;
				
				// write to output buffer
				out.write(buf, 0, len); //out.flush();

				progMon.count(len)
			}
			
			progMon.end()
			
			log.info "Finished sending file!"
			
			fis.close()
			fis = null
			// send '\0'
			buf[0]=0
			out.write(buf, 0, 1)
			out.flush()
			
			if (checkResult(input)!=0){
				log.error("Error returned after sending file to $host!")
				throw new RuntimeException("Error returned after sending file to $host!")
				return false
			}
			
			out.close();

			channel.disconnect();
			if (!leaveSessionOpen) session.disconnect()
			true
		}
		catch(Exception e){
			try{if(fis!=null)fis.close();}catch(Exception ee){}
			handleException(e)
			return false
		}
	}
	
	/**
	 * Converts bytes into human readable format
	 * @param bytes
	 * @return Human readable string in KB, MB, GB, etc...
	 */
	public static String humanReadableByteCount(long bytes, boolean si = false) {
		int unit = si ? 1000 : 1024
		if (bytes < unit) return bytes + " B"
		int exp = (int) (Math.log(bytes) / Math.log(unit))
		String pre = (si ? "kMGTPE" : "KMGTPE")[exp-1] //+ (si ? "" : "i")
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre)
	}

	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// EXEC REMOTE (non-sudo)
	
	public boolean execRemote(String command) {

		checkHostAndUser()
		
		String sudo_pass = null

		log.info "Executing SUDO command '$command' for ${user}@${host}"

		try {

			createSession()

			Channel channel=session.openChannel("exec");

			((ChannelExec)channel).setCommand(command);

			InputStream input = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			InputStream err = ((ChannelExec)channel).getErrStream()

			log.info "Connecting to ${user}@${host}"
			channel.connect();

			if (!channel.isConnected()) {
				log.error "Error: unable to connect to ${user}@${host}"
				return false
			}

			byte[] tmp=new byte[1024];
			String str = ""
			String error = ""

			while(true) {

				while(input.available()>0) {
					int i=input.read(tmp, 0, 1024)
					str += new String(tmp, 0, i)
					if (i<0) break
				}

				while(err.available()>0) {
					int i=err.read(tmp, 0, 1024)
					error += new String(tmp, 0, i)
					if (i<0) break
				}

				if (error != '') {
					log.error "Unable to execute command. " + error
					throw new RuntimeException("Unable to execute command. " + error)
					break
				}

				if(channel.isClosed()) {
					break
				}

				log.info "Waiting for response from $host."
				
				try{Thread.sleep(1000);}catch(Exception ee){}
			}

			if (str!="") log.info("Output from $host:\n" + str)
			else log.info "No output."

			log.info("exit-status: "+channel.getExitStatus())
			
			channel.disconnect()
			if (!leaveSessionOpen) session.disconnect()
			true
		}
		catch(Exception e){
			handleException(e)
			false
		}


	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	// EXEC REMOTE SUDO

	public boolean execRemoteSudo(String command) {

		checkHostAndUser()
		
		String sudo_pass = null

		log.info "Executing command '$command' for ${user}@${host}"

		try {

			createSession()

			Channel channel=session.openChannel("exec");

			// man sudo
			//   -S  The -S (stdin) option causes sudo to read the password from the
			//       standard input instead of the terminal device.
			//   -p  The -p (prompt) option allows you to override the default
			//       password prompt and use a custom one.
			((ChannelExec)channel).setCommand("sudo -S -p '' "+command);

			InputStream input = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			InputStream err = ((ChannelExec)channel).getErrStream()

			log.info "Connecting to ${user}@${host}"
			channel.connect();

			if (!channel.isConnected()) {
				log.error "Error: unable to connect to ${user}@${host}"
				return false
			}

			if (sudoPassDifferent) {
				
				sudo_pass = prefs.getSudoPassword(user,host)
				
				if (sudo_pass == null) {

					def console = System.console()
					String response = ''
					if (console) {
						response = new String(console.readPassword("> Please enter your SUDO password: "))
					} else {
						log.error "Cannot get console."
					}
					sudo_pass = response.trim()

					if (promptToSavePass && ui.promptYesNo("Would you like to save the SUDO password (encrypted) for ${user}@${host} in the system prefs?")) {
						prefs.storeSudoPassword(ui.getPassword(), user,host)
						log.info "Password saved (encrypted) for SUDO ${user}@${host}"
					}
					
				}
				else {
					log.info "Using saved password for SUDO ${user}@${host}"
				}
	
			}
			else {
				sudo_pass = ui.getPassword()
			}
			
			out.write((sudo_pass+"\n").getBytes());
			out.flush()

			byte[] tmp=new byte[1024];
			String str = ""
			String error = ""

			while(true) {

				while(input.available()>0) {
					int i=input.read(tmp, 0, 1024)
					str += new String(tmp, 0, i)
					if (i<0) break
				}

				while(err.available()>0) {
					int i=err.read(tmp, 0, 1024)
					error += new String(tmp, 0, i)
					if (i<0) break
				}

				if (error.contains('try again')) {
					log.error "Unable to execute sudo command. Wrong password."
					throw new RuntimeException("Unable to execute sudo command. Wrong password.")
					break
				}
				else if (error != '') {
					log.error "Unable to execute sudo command. " + error
					throw new RuntimeException("Unable to execute sudo command. " + error)
					break
				}

				if(channel.isClosed()) {
					break
				}
				
				log.info "Waiting for response from $host."
				
				try{Thread.sleep(1000);}catch(Exception ee){}
			}
			
			if (str!="") log.info("Output from $host:\n" + str)
			else log.info "No output."
			
			log.info("exit-status: "+channel.getExitStatus())
			
			channel.disconnect()
			if (!leaveSessionOpen) session.disconnect()
			true
		}
		catch(Exception e){
			handleException(e)
			false
		}

	}
}