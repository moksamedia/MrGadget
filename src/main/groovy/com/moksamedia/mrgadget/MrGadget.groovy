package com.moksamedia.mrgadget

import java.text.DecimalFormat

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
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
	
	int port = 22
	
	JSch jsch = new JSch()
	Prefs prefs
	UserInfo ui
	
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
		
	String password = null, sudoPassword = null, prefsEncryptionKey = null
	String privateKey = null
	
	boolean clearAllPasswords = false
	
	String version = "NONE SUPPLIED"
	
	String commandUsed = ''
	String standardOutput = ''
	String errorOutput = ''
	String exitStatus = ''
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTOR
	
	/**
	 * Default Constructor
	 * 
	 * @param host host name or ip address of remote server (REQUIRED to connect, but can be set post-constructor)
	 * @param user username used to connect to remote server (REQUIRED to connect, but can be set post-constructor)
	 * @param leaveSessionOpen if multiple commands are to be executed, this can be used to avoid having to re-connect for each action; default FALSE
	 * @param sudoPassDifferent set to true if the sudo password is different than the user login password; default FALSE
	 * @param promptToSavePass set to false if you don't want MrG to prompt you to save your password; default TRUE
	 * @param strictHostKeyChecking set to false to disable strict host key checking; defaults to TRUE
	 * @param showProgressDialog set to false to suppress the Swing progress dialog box while transferring files; default TRUE
	 * @param preserveTimestamp set to true to preserve timestamp of file copied to remote server; default FALSE
	 * @param logProgressGranularity an int between 0 - 100 that controls how often, in percentage, the file sending progress is reported (log.info)
	 * @param clearAllPasswords if true, all stored passwords will as well as the encryption key will be erased
	 * @param prefsEncryptionKey if you would like to use a passed-in encryption password instead of an auto-generated one
	 */
	public MrGadget(def params = [:]) {
		
		ui = new MyUserInfo()
		
		// set host and user, if supplied
		this.host = params.host
		this.user = params.user
		
		setParams(params)
		
		prefs = new Prefs(val:prefsEncryptionKey, clearAllPasswords:clearAllPasswords)
		
		// decimal format
		decFormat = new DecimalFormat("#,##0.00")
		
		version = getClass().getResourceAsStream("/version")?.text
	
		log.info "Using MrGadget version $version"			
	}
	
	public void setParams(def params = [:]) {
		def toSet = params.findAll { k, v -> k in this.metaClass.properties*.name && k != 'class' && k != 'metaClass'}
		toSet.each { propName, val ->
			log.info "Setting MrGadget.$propName=$val"
			this."$propName" = val
		}
	}

	Closure clearOutputs = {
		standardOutput = ''
		errorOutput = ''
		exitStatus = ''
		commandUsed = ''
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CREATE SESSION
	// - this is used by all three methods to create a (potentially common) session object
	// - leaveSessionOpen can be set to true to avoid having to reconnect to host while executing
	//   a sequence of actions
	// - if leaveSessionOpen is set to true, then the session should be closed upon completion
	
	
	// create a session and connect to host
	Closure createSession = {
		
		clearOutputs() // createSession is called before every execution, so this will always clear outputs
		
		// don't connect if we already have a session
		if (session?.isConnected()) return

        if (privateKey != null) {
            log.info("Adding private key at " + privateKey);
            jsch.addIdentity(privateKey)
        }

        session = jsch.getSession(user, host, port);

		log.info "********** MrGadget v$version Session Created **********"
		
		if (strictHostKeyChecking) {
			log.debug "Strict host key checking is ON"
			session.setConfig("StrictHostKeyChecking", "yes");
		}
		else {
			log.debug "Strict host key checking is OFF"
			session.setConfig("StrictHostKeyChecking", "no")
		}

		session.setUserInfo(ui)

		String pass 
		
		// check for provided password
		if (password != null) {
			pass = password
			log.info "Using provided password ($pass) for ${user}@${host}"
		}
		//check for saved password
		else {
			pass = prefs.getPassword(user, host)
			if (pass != null) log.info "Using saved password for ${user}@${host}"
		}
						
		// if we have a saved password
		if (pass != null) {
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
				log.debug "Password saved (encrypted) for ${user}@${host}"
			}

		}

	}

	/**
	 * Close the JSch session (only needed if leaveSessionOpen = true)
	 */
	public void closeSession() {
		if (session?.isConnected()) {
			session.disconnect()
			log.info "********** MrGadget Session Closed **********"
		}
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
		throw new RuntimeException("Error attempting to execute command!", e)
	}
		
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// CLEAR PASSWORDS
	
	/**
	 * Clear all passwords saved in prefs, as well as the password key
	 */
	public void clearAllPasswords() {
		prefs.resetPrefs()
	}
	
	/**
	 * Clear password for a user and host saved in prefs; key is not cleared
	 */
	public void clearPasswordsForUserAtHost(String user, String host) {
		prefs.removeAllPrefsForUserAtHost(user, host)
	}

	/**
	 * Clear password for a user at all hosts saved in prefs; key is not cleared
	 */
	public void clearPasswordsForUser(String user) {
		prefs.removeAllPrefsForUser(user)
	}

	/**
	 * Copy a file to a remote server using rsync.
	 * 
	 * @param localFile the full path to the local file to send
	 * @param remoteFile the full path to the remote file to create
	 * @param progress report progress on command line (log.info) while sending file
	 * @param partial allows resumption of partial uploads
	 * @param owner set the owner of the remote file (REQUIRES SUDO -- see docs)
	 * @param group set the group of the remote file (REQUIRES SUDO -- see docs)
	 * @param chmod set the permissions of the remote file (REQUIRES SUDO -- see docs)
	 * @param numericIds use numeric group and user ids instead of names
	 * @param rsyncPathLocal the path to the local rsync command (defaults to '/usr/bin/rsync')
	 * @param rsyncPathRemote the path to the rsync command on the remote server (if using sudo and setting custom path, must use sudo rsync)
	 * @param additionalArgs any additional arguments to include (as a list of strings, NOT a single string)
	 * @return true if success
	 */
	public boolean copyToRemoteRSYNC(def params = [:]) {
		
		checkHostAndUser()
		
		String owner = params.owner
		String group = params.group
		String chmod = params.chmod
		boolean numericIds = params.get('numericIds', false)
		String rsyncPathRemote = params.rsyncPath
		String rsyncPathLocal = params.get('rsyncPathLocal', '/usr/bin/rsync')
		boolean progress = params.get('progress', true)
		boolean partial = params.get('partial', false)
		String localFile = params.localFile
		String remoteFile = params.remoteFile
		
		def additionalArgs = params.additionalArgs
		
		// reality check: we need a local file and a remote file
		if (!params.containsKey('localFile') || !params.containsKey('remoteFile')) {
			log.error("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			throw new RuntimeException("Error: remoteFile and localFile must be specified. localFile=$params.localFile, remoteFile=$params.remoteFile")
			return false
		}

		if (rsyncPathLocal.toLowerCase() == 'auto') rsyncPathLocal = 'which rsync'.execute().text.trim()
				
		log.info "Using rsync: $rsyncPathLocal"
		
		def command = ["$rsyncPathLocal".toString()]
		
		if (numericIds) command += '--numeric-ids'
		
		if (chmod!=null) command += "--chmod=$chmod".toString(); command +='--perms'
		if (group!=null) command += "--group=$group".toString()
		if (owner!=null) command += "--owner=$owner".toString()
		
		/*
		 * NOTE: I was having a lot of trouble getting the rsync-path to work property when building the command
		 * as an array (it worked fine as a string). The error I was getting was: "bash: sudo rsync: command not found".
		 * This was caused by the bash shell interpreting 'sudo rsync' as a single token. Strang. Not sure why
		 * this happens, but the solution was to NOT QUOTE the sudo rsync part of the argument.
		 * YES -> "--rsync-path=sudo rsync"
		 * NO -> "--rsync-path=\"sudo rsync\""
		 * 
		 */
		
		if (rsyncPathRemote!=null) command += "--rsync-path=$rsyncPathRemote".toString()
		else if (chmod!=null || group!=null || owner!=null) command += "--rsync-path=sudo rsync".toString()
		
		if (partial	) command += '--partial'.toString()
		if (progress!=null) command += '--progress'.toString()
		
		if (additionalArgs != null) command += additionalArgs
		
		command += "$localFile".toString()
		command += "${user}@${host}:${remoteFile}".toString()
		
		commandUsed = command.inject("") {acc, val -> acc += val+" "; acc} 		
		
		log.info "RSYNC COMMAND: $commandUsed"
		
		Closure getPercent = { String val ->
			
			String result = ""
			def vals = val.split()
			
			if (vals.size() == 4) {
				result = vals[1].replaceAll('%','')
			}

			if (result == "") return -1
			else return result as int			
		}
		
		// this allows us to see the output from the command as it executes
		Closure executeProc = { def cmd ->
			def proc = cmd.execute()

			proc.in.eachLine {line ->
				if (progress && getPercent(line) % logProgressGranularity == 0) log.info line
			}

			log.info proc.err.text

			def exitVal = proc.exitValue()
			log.info "exit value: $exitVal"
			exitVal
		}
		
		executeProc(command) == 0 // return true if success
		
	}
	
	/**
	 * Copies a file to a remote server using SFTP
	 * @param localFile : full path to the local file to copy
	 * @param remoteFile : full path to the destination file (including filename and extension)
	 * @param showProgressDialog : set to false to suppress the Swing progress dialog box while transferring files; default TRUE
	 * @param logProgressGranularity : an int between 0 - 100 that controls how often, in percentage, the file sending progress is reported (log.info)
	 * @return true if success
	 */
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

			commandUsed = "sftp $localFilePath $remoteFilePath"
			
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
	 * Sends a file to a remote server using SSH SCP
	 * @param localFile: the full path of the local file to send
	 * @param remoteFile: the full path of the file to create on the remote server (including the file name and extension)
	 * @param showProgressDialog: show the Swing dialog with the progress bar (closing the dialog does NOT abort the operation)
	 * @param preserveTimestamp: file copied to remote destination has same timestamp as local file
	 * @param logProgressGranularity: integer percentage; progress is logged according to this size (10 = every 10%, 100 = start and finish)
	 * @return true if success
	 */
	public boolean copyToRemoteSCP(def params = [:]) {

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
			String command = 'scp ' + (preserveTimestamp ? '-p' :'') + ' -t ' + remoteFilePath
			Channel channel = session.openChannel('exec');
			((ChannelExec)channel).setCommand(command);
			
			log.debug command
			commandUsed += command
			
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
				commandUsed += command
				
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
			
			log.info command
			commandUsed += ' ' + command
			
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
	
	/**
	 * Executes a command on a remote server. Host and user should already be set.
	 * @param command the command to execute
	 * @return true if success
	 */
	public boolean execRemote(String command) {

		checkHostAndUser()
		
		log.info "Executing command '$command' for ${user}@${host}"

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

			commandUsed = command
			
			while(true) {

				while(input.available()>0){
					int i=input.read(tmp, 0, 1024)
					if(i<0)break
					str += new String(tmp, 0, i)
					log.info str
				}

				while(err.available()>0){
					int i=err.read(tmp, 0, 1024)
					if(i<0)break
					error += new String(tmp, 0, i)
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
			
			standardOutput = str
			errorOutput = error
			exitStatus = channel.getExitStatus()
			
			log.info("exit-status: "+exitStatus)
			
			
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

	/**
	 * Executes a SUDO command on a remote server. Host and user should already be set.
	 * @param command the command to execute
	 * @return true if success
	 */
	public boolean execRemoteSudo(String command) {

		checkHostAndUser()
		
		String sudo_pass = null

		command = "sudo -S -p '' " + command
		
		log.info "Executing command '$command' for ${user}@${host}"
		
		log.info "commandUsed = $commandUsed"
		
		try {

			createSession()

			Channel channel=session.openChannel("exec");

			// man sudo
			//   -S  The -S (stdin) option causes sudo to read the password from the
			//       standard input instead of the terminal device.
			//   -p  The -p (prompt) option allows you to override the default
			//       password prompt and use a custom one.
			((ChannelExec)channel).setCommand(command);

			InputStream input = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			InputStream err = ((ChannelExec)channel).getErrStream()

			commandUsed = command
			
			log.info "Connecting to ${user}@${host}"
			channel.connect();

			if (!channel.isConnected()) {
				log.error "Error: unable to connect to ${user}@${host}"
				return false
			}

			if (sudoPassDifferent) {
				
				sudo_pass = sudoPassword ?: prefs.getSudoPassword(user,host)
				
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

				while(input.available()>0){
					int i=input.read(tmp, 0, 1024)
					if(i<0)break
					str += new String(tmp, 0, i)
					log.info str
				}

				while(err.available()>0){
					int i=err.read(tmp, 0, 1024)
					if(i<0)break
					error += new String(tmp, 0, i)
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
			
			standardOutput = str
			errorOutput = error
			exitStatus = channel.getExitStatus()
			
			log.info("exit-status: "+exitStatus)
			
			
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