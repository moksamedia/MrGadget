package com.moksamedia.mrgadget

import static org.junit.Assert.assertEquals
import groovy.util.logging.Slf4j

import java.util.concurrent.CountDownLatch

import org.apache.sshd.SshServer
import org.apache.sshd.common.Factory
import org.apache.sshd.common.keyprovider.FileKeyPairProvider
import org.apache.sshd.server.Command
import org.apache.sshd.server.CommandFactory
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.apache.sshd.server.shell.ProcessShellFactory
import org.bouncycastle.openssl.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test

import com.jcraft.jsch.*
import com.moksamedia.mrgadget.EchoShellFactory.EchoShell


@Slf4j
class MrGadgetTest {

	static SshServer sshd
	static int port = 50000

	@Test
	void testPassword() {
		
		MrGadget mrg = new MrGadget(user:'cantgetnosleep', host:'www.moksamedia.com', strictHostKeyChecking:false, leaveSessionOpen:true)
		mrg.copyToRemoteSFTP(localFile:"/Users/ach857/java/blograt/build/libs/blograt.war", remoteFile:"/websites/todeploy/temp.war")
		mrg.closeSession()
		
	}
	
	@Test
	void testSetParams() {
		
		MrGadget mrg = new MrGadget()
		
		String user = "thisuser"
		String host = "www.awesome.com"
		boolean leaveSessionOpen = true
		boolean sudoPassDifferent = true
		boolean promptToSavePass = false
		boolean strictHostKeyChecking = false
		boolean showProgressDialog = false
		boolean preserveTimestamp = true
		int logProgressGranularity = 20
		String password = 'somepass'
		String sudoPassword = 'sudopass'
		String prefsEncryptionKey = 'assdfoqwiehownqg'
		boolean clearAllPasswords = true
		
		mrg.setParams(
			user:user,
			host:host,
			leaveSessionOpen:leaveSessionOpen,
			sudoPassDifferent:sudoPassDifferent,
			promptToSavePass:promptToSavePass,
			strictHostKeyChecking:strictHostKeyChecking,
			showProgressDialog:showProgressDialog,
			preserveTimestamp:preserveTimestamp,
			logProgressGranularity:logProgressGranularity,
			password:password,
			sudoPassword:sudoPassword,
			prefsEncryptionKey:prefsEncryptionKey,
			clearAllPasswords:clearAllPasswords
			)
		
		assert mrg.user == user
		assert mrg.host == host
		assert mrg.leaveSessionOpen == leaveSessionOpen
		assert mrg.sudoPassDifferent == sudoPassDifferent
		assert mrg.promptToSavePass == promptToSavePass
		assert mrg.strictHostKeyChecking == strictHostKeyChecking
		assert mrg.showProgressDialog == showProgressDialog
		assert mrg.preserveTimestamp == preserveTimestamp
		assert mrg.logProgressGranularity == logProgressGranularity
		assert mrg.password == password
		assert mrg.sudoPassword == sudoPassword
		assert mrg.prefsEncryptionKey == prefsEncryptionKey
		assert mrg.clearAllPasswords == clearAllPasswords
		
	}
	
	@Test
	void testConstructor() {
				
		String user = "thisuser"
		String host = "www.awesome.com"
		boolean leaveSessionOpen = true
		boolean sudoPassDifferent = true
		boolean promptToSavePass = false
		boolean strictHostKeyChecking = false
		boolean showProgressDialog = false
		boolean preserveTimestamp = true
		int logProgressGranularity = 20
		String password = 'somepass'
		String sudoPassword = 'sudopass'
		String prefsEncryptionKey = 'assdfoqwiehownqg'
		boolean clearAllPasswords = true
		
		MrGadget mrg = new MrGadget(
			user:user,
			host:host,
			leaveSessionOpen:leaveSessionOpen,
			sudoPassDifferent:sudoPassDifferent,
			promptToSavePass:promptToSavePass,
			strictHostKeyChecking:strictHostKeyChecking,
			showProgressDialog:showProgressDialog,
			preserveTimestamp:preserveTimestamp,
			logProgressGranularity:logProgressGranularity,
			password:password,
			sudoPassword:sudoPassword,
			prefsEncryptionKey:prefsEncryptionKey,
			clearAllPasswords:clearAllPasswords
			)
		
		assert mrg.user == user
		assert mrg.host == host
		assert mrg.leaveSessionOpen == leaveSessionOpen
		assert mrg.sudoPassDifferent == sudoPassDifferent
		assert mrg.promptToSavePass == promptToSavePass
		assert mrg.strictHostKeyChecking == strictHostKeyChecking
		assert mrg.showProgressDialog == showProgressDialog
		assert mrg.preserveTimestamp == preserveTimestamp
		assert mrg.logProgressGranularity == logProgressGranularity
		assert mrg.password == password
		assert mrg.sudoPassword == sudoPassword
		assert mrg.prefsEncryptionKey == prefsEncryptionKey
		assert mrg.clearAllPasswords == clearAllPasswords
		
	}
	
	@BeforeClass
	static void doBefore() {
		//JSch.setLogger(new MyLogger());
		
		sshd = SshServer.setUpDefaultServer();
		sshd.setPort(port);
		sshd.setKeyPairProvider(new FileKeyPairProvider(["src/test/resources/hostkey.pem"] as String[]));
		sshd.setSubsystemFactories([new SftpSubsystem.Factory()]);
		sshd.setCommandFactory(new MySCPCommandFactory(new TestCommandFactory()));
		sshd.setShellFactory(new TestEchoShellFactory());
		sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
		sshd.start();
	}
	
	@AfterClass
	static void doAfter() {
		sshd.stop()
	}
	
	//@Ignore	
	@Test
	void testExecRemote() {
		
		String user = 'testuser'
		String host = 'localhost'
		String password = 'testuser'
		boolean leaveSessionOpen = true
		boolean strictHostKeyChecking = false
		
		MrGadget mrg = new MrGadget(
			user:user,
			host:host,
			password:password,
			leaveSessionOpen:leaveSessionOpen,
			strictHostKeyChecking:strictHostKeyChecking
			)
	
		mrg.port = MrGadgetTest.port
		
		TestUserInfo ui = new TestUserInfo()
		
		ui.getPassphraseClosure = { message -> log.info message; password }
		ui.getPasswordClosure = { message -> log.info message; password }
		ui.promptYesNoClosure = { log.info message; true }
		
		mrg.ui = new TestUserInfo()
		
		mrg.execRemote('ls sshtest')
		
		mrg.commandUsed.trim() == 'ls sshtest'
		assert mrg.standardOutput.replaceAll('\n', ' ').trim() == 'remotefile.txt testfile1.txt testfile2.txt'
		assert mrg.errorOutput == ''
		
		mrg.closeSession()

	}

	@Test
	void testExecRemoteSudo() {
					
		String user = 'testuser'
		String host = 'localhost'
		String password = 'testuser'
		boolean leaveSessionOpen = true
		boolean strictHostKeyChecking = false
		
		MrGadget mrg = new MrGadget(
			user:user,
			host:host,
			password:password,
			leaveSessionOpen:leaveSessionOpen,
			strictHostKeyChecking:strictHostKeyChecking
			)
	
		mrg.port = MrGadgetTest.port
		
		TestUserInfo ui = new TestUserInfo()
		
		ui.getPassphraseClosure = { message -> log.info message; password }
		ui.getPasswordClosure = { message -> log.info message; password }
		ui.promptYesNoClosure = { log.info message; true }
		
		mrg.ui = new TestUserInfo()
		
		mrg.execRemoteSudo('ls stupid')
		assert mrg.standardOutput.trim() == "sudo -S -p '' ls stupid"
		assert mrg.commandUsed.trim() == "sudo -S -p '' ls stupid"
		assert mrg.errorOutput == ''
		
		mrg.closeSession()

	}


	@Test
	void testSCP() {

		String user = 'testuser'
		String host = 'localhost'
		String password = 'testuser'
		boolean leaveSessionOpen = false
		boolean strictHostKeyChecking = false
		
		String localFile = 'sshtest/testfile1.txt'
		String remoteFile = 'sshtest/remotefile.txt'

		
		File from = new File(localFile)
		localFile = from.getAbsolutePath()
				
		def params = [
			user:user,
			host:host,
			password:password,
			leaveSessionOpen:leaveSessionOpen,
			strictHostKeyChecking:strictHostKeyChecking,
			localFile:localFile,
			remoteFile:remoteFile,
			showProgressDialog:false
			]
		
		MrGadget mrg = new MrGadget(params)

		mrg.port = MrGadgetTest.port

		TestUserInfo ui = new TestUserInfo()

		ui.getPassphraseClosure = { message -> log.info message; password }
		ui.getPasswordClosure = { message -> log.info message; password }
		ui.promptYesNoClosure = { log.info message; true }

		mrg.ui = new TestUserInfo()

		mrg.copyToRemoteSCP(params)
			
		mrg.closeSession()
		
		log.info mrg.commandUsed
		assert mrg.commandUsed.trim() == 'scp  -t sshtest/remotefile.txt C0644 3735 testfile1.txt'
		assert mrg.errorOutput == ''

		from = new File(localFile)
		
		/*
		 * Locally SCP doesn't seem to respect the -t option and just copies the file
		 * with the same name to the base directory of the project. Works remotely, though.
		 * 
		 */
		File to = new File('testfile1.txt')		
		assert from.text == to.text
		to.delete()
		
	}


	@Test
	void testSFTP() {

		String user = 'testuser'
		String host = 'localhost'
		String password = 'testuser'
		boolean leaveSessionOpen = true
		boolean strictHostKeyChecking = false

		String localFile = 'sshtest/testfile2.txt'
		String remoteFile = 'sshtest/remotefile.txt'

		File to = new File(remoteFile)
		to.write('replaceing text')
		
		def params = [
					user:user,
					host:host,
					password:password,
					leaveSessionOpen:leaveSessionOpen,
					strictHostKeyChecking:strictHostKeyChecking,
					localFile:localFile,
					remoteFile:remoteFile,
					showProgressDialog:false
				]

		MrGadget mrg = new MrGadget(params)

		mrg.port = MrGadgetTest.port

		TestUserInfo ui = new TestUserInfo()

		ui.getPassphraseClosure = { message -> log.info message; password }
		ui.getPasswordClosure = { message -> log.info message; password }
		ui.promptYesNoClosure = { log.info message; true }

		mrg.ui = new TestUserInfo()

		mrg.copyToRemoteSFTP(params)

		log.info 'Command used:' + mrg.commandUsed
		assert mrg.commandUsed.trim() == 'sftp sshtest/testfile2.txt sshtest/remotefile.txt'
		assert mrg.errorOutput == ''

		File from = new File(localFile)
		to = new File(remoteFile)
		assert from.text == to.text
		
		mrg.closeSession()

	}


}


public class MyLogger implements com.jcraft.jsch.Logger {
	static java.util.Hashtable name=new java.util.Hashtable();
	static{
	  name.put(new Integer(JSch.logger.DEBUG), "DEBUG: ");
	  name.put(new Integer(JSch.logger.INFO), "INFO: ");
	  name.put(new Integer(JSch.logger.WARN), "WARN: ");
	  name.put(new Integer(JSch.logger.ERROR), "ERROR: ");
	  name.put(new Integer(JSch.logger.FATAL), "FATAL: ");
	}
	public boolean isEnabled(int level){
	  return true;
	}
	public void log(int level, String message){
	  println message
	}
  }

public class BogusPasswordAuthenticator implements PasswordAuthenticator {
	
		public boolean authenticate(String username, String password, ServerSession session) {
			return username != null && username.equals(password);
		}
	}

public class TestEchoShellFactory extends EchoShellFactory {
	@Override
	public Command create() {
		return new TestEchoShell();
	}
	public static class TestEchoShell extends EchoShell {

		public static CountDownLatch latch = new CountDownLatch(1);

		@Override
		public void destroy() {
			if (latch != null) {
				latch.countDown();
			}
			super.destroy();
		}
	}
}

public class TestCommandFactory implements CommandFactory {
	public Command createCommand(String command) {
		if (command.contains('sudo')) {
			new ProcessShellFactory(['echo', "$command"] as String[]).create()
		}
		else {
			new ProcessShellFactory(command.split(' ')).create()
		}
		//return new ProcessShellFactory(['echo', "$command"] as String[]).create();
	}
}


@Slf4j
public class EchoShellFactory implements Factory<Command> {
	
		public Command create() {
			return new EchoShell();
		}
	
		public static class EchoShell implements Command, Runnable {
				
			private InputStream input;
			private OutputStream out;
			private OutputStream err;
			private ExitCallback callback;
			private Environment environment;
			private Thread thread;
	
			public InputStream getIn() {
				return input;
			}
	
			public OutputStream getOut() {
				return out;
			}
	
			public OutputStream getErr() {
				return err;
			}
	
			public Environment getEnvironment() {
				return environment;
			}
	
			public void setInputStream(InputStream input) {
				this.input = input;
			}
	
			public void setOutputStream(OutputStream out) {
				this.out = out;
			}
	
			public void setErrorStream(OutputStream err) {
				this.err = err;
			}
	
			public void setExitCallback(ExitCallback callback) {
				this.callback = callback;
			}
	
			public void start(Environment env) throws IOException {
				environment = env;
				thread = new Thread(this, "EchoShell");
				thread.start();
			}
	
			public void destroy() {
				thread.interrupt();
			}
	
		public void run() {
			BufferedReader r = new BufferedReader(new InputStreamReader(input));
			try {
				for (;;) {
					String s = r.readLine();
					out.write((s + "\n").getBytes())
					out.flush()
					return
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				callback.onExit(0);
			}
		}
		}
	}
