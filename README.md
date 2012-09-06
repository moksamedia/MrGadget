DESCRIPTION:

MrGadget is a very lightweight utility library designed to do 4 things: 1) send a file to a remote server via SCP, 2) send a file to a remote server via SFTP, 3) execute a command on a remote server, 4) execute a SUDO command on a remote server. It uses the JSch library and leans heavily on the example code on the JSch website. Although MrGadget was built for my use in Gradle, it is a self-contained set of Groovy classes that should be useful anywhere.

File uploading allows progress to be reported via 'log.info' statements or via a Java Swing dialog box or both. 

MrGadget will use the System.console to prompt the user for necessary passwords, and there is an option to save these passwords, using encryption, in the system preferences. The encryption scheme is better than plain text, but rather basic, and as this is open-source, NO GUARANTEE is made regarding the security of the encryption. It is certainly better than saving the passwords in plain text in a config file or build file, but an interested person could reverse engineer the key used and discover the password. SO USE THIS FEATURE AT YOUR OWN RISK.


USAGE WITH GRADLE:

Download the project or jar.
Add the import statement and the dependency to the gradle buildscript block as shown:

	import com.moksamedia.mrgadget.MrGadget

	buildscript {
	
	  repositories {
	    mavenCentral()
		flatDir {
			dirs	"path/to/jar"
		}
	  }
    
	  dependencies {
		classpath 'com.moksamedia.mrgadget:mrgadget:0.2'
	  }

	}

Use him in some tasks!

	task copyToServerOnly {

		doLast {
	
			MrGadget mrg = new MrGadget(
				user:'someuser', 
				host:'www.someuser.com', 
				strictHostKeyChecking:false)
			
			logger.info "SENDING FILE"
			mrg.copyToRemoteSFTP(localFile:"$rootDir/build/libs/someapp.war", remoteFile:"/websites/someapp.war")
		
		}
	}

	task deployToServerAndFix {
	
		doLast {
		
			MrGadget mrg = new MrGadget(
				user:'someuser', 
				host:'www.someuser.com', 
				strictHostKeyChecking:false, 
				leaveSessionOpen:true)
				
			logger.info "SENDING FILE"
			mrg.copyToRemoteSFTP(localFile:"$rootDir/build/libs/someapp.war", remoteFile:"/websites/someapp.war")
			
			logger.info "SETTING OWNER OF WAR"
			mrg.execRemoteSudo("chown tomcat6:tomcat6 /websites/someapp.war")
			
			logger.info "SETTING PERMISSION OF WAR"
			mrg.execRemoteSudo("chmod 775 /websites/someapp.war")
			
			logger.info "RESTARTING TOMCAT"
			mrg.execRemoteSudo("service tomcat6 restart")
						
			logger.info "FINISHED"
			mrg.closeSession()
		}
	}

	task clearPasswords << {
		MrGadget mrg = new MrGadget(
			user:'someuser',
			host:'www.someuser.com',
			clearAllPasswords:true)
	}

