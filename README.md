# Description

MrGadget is a very lightweight utility library designed to do 4 things: 1) send a file to a remote server via SCP, 2) send a file to a remote server via SFTP, 3) execute a command on a remote server, 4) execute a SUDO command on a remote server. It uses the JSch library and leans heavily on the example code on the JSch website. Although MrGadget was built for my use in Gradle, it is a self-contained set of Groovy classes that should be useful anywhere.

File uploading allows progress to be reported via 'log.info' statements or via a Java Swing dialog box or both. 

MrGadget will use the System.console to prompt the user for necessary passwords, and there is an option to save these passwords, using encryption, in the system preferences. The encryption scheme is better than plain text, but rather basic, and as this is open-source, NO GUARANTEE is made regarding the security of the encryption. It is certainly better than saving the passwords in plain text in a config file or build file, but an interested person could reverse engineer the key used and discover the password. SO USE THIS FEATURE AT YOUR OWN RISK.

# Usage (in general)

MrGadget is a plain-old Groovy library class and there's no reason he can't be used pretty much anywhere there's a JVM. I just happened to write him for my own use in gradle. Example Groovy usage would be:

	class ReallySimpleClass {

		MrGadget mrg
	
		public ReallySimpleClass(String user, String host) {
			mrg = new MrGadget(user:user, host:host)
			mrg.strictHostKeyChecking = false
			mrg.showProgressDialog = false 
			mrg.logProgressGranularity = 10 // log.info file upload progress every 10%
		}

		void doIt() {
			mrg.copyToRemoteSFTP(localFile:'some/local/file.zip', remoteFile:'some/remote/file.zip')
		}


	} 

# Usage with Gradle

Import from Maven Central

	import com.moksamedia.mrgadget.MrGadget

	buildscript {
	
	  repositories {
	    mavenCentral()
	  }
    
	  dependencies {
		classpath 'com.moksamedia.mrgadget:mrgadget:0.2.1' // OR CURRENT VERSION
	  }

	}


Download the jar-with-dependencies
Add the import statement and the dependency to the gradle buildscript block as shown:

	import com.moksamedia.mrgadget.MrGadget

	buildscript {
	
	  repositories {
	    mavenCentral()
		flatDir {
			dirs "path/to/jar"
		}
	  }
    
	  dependencies {
		classpath 'com.moksamedia.mrgadget:mrgadget:0.2.1:jar-with-dependencies' // OR CURRENT VERSION
	  }

	}

You could also import directly from the GitHub repo:

	buildscript {

		repositories {
			mavenCentral()
			add(new org.apache.ivy.plugins.resolver.URLResolver()) {
				name = 'mrgadget-plugin'
				basePath = 'https://raw.github.com/moksamedia/mrgadget-gradle-plugin/master/repo'
				addArtifactPattern "${basePath}/[organization]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
			}
		}

	  dependencies {
		classpath 'com_moksamedia:mrgadget:0.2.1:jar-with-dependencies' // OR CURRENT VERSION
	  }

	}

Of course, all of this assumes you're using MrGadget in the buildscript itself. To simply add his as a dependency to a project all you need is:

	repositories {
	  mavenCentral()
	}

	dependencies {
		compile 'com.moksamedia.mrgadget:mrgadget:0.2.1' // OR CURRENT VERSION
	}



# Dependencies

	groovy 'org.codehaus.groovy:groovy:1.8.6' ext { fatJarExclude = true }	// excluded from fatJar
	compile 'org.slf4j:slf4j-simple:1.6.3' ext { fatJarExclude = true }		// excluded from fatJar
	compile 'com.jcraft:jsch:0.1.48'	// JSch is the SSH java package
	compile 'org.jasypt:jasypt-acegisecurity:1.9.0'	// for encrypting the stored passwords

	<dependency>
		<groupId>org.jasypt</groupId>
		<artifactId>jasypt-acegisecurity</artifactId>
		<version>1.9.0</version>
		<scope>compile</scope>
	</dependency>
	<dependency>
		<groupId>com.jcraft</groupId>
		<artifactId>jsch</artifactId>
		<version>0.1.48</version>
		<scope>compile</scope>
	</dependency>
	<dependency>
		<groupId>org.slf4j</groupId>
		<artifactId>slf4j-simple</artifactId>
		<version>1.6.3</version>
		<scope>compile</scope>
	</dependency>

# Use him in some tasks!

	task copyToServerOnly << {
	
		MrGadget mrg = new MrGadget(
			user:'someuser', 					// user and host must always be set
			host:'www.someuser.com', 
			strictHostKeyChecking:false)		// can turn off strict host key checking to avoid host key errors
		
		logger.info "SENDING FILE"
		mrg.copyToRemoteSFTP(localFile:"$rootDir/build/libs/someapp.war", remoteFile:"/websites/someapp.war")

	}
	
	task copyToServerOnlyWithCustomKey << {
	
		MrGadget mrg = new MrGadget(
			user:'someuser', 				
			host:'www.someuser.com', 
			strictHostKeyChecking:false,	
			prefsEncryptionKey: someCustomKey) // can specify a custom key to use to encrypt passwords		
		
		logger.info "SENDING FILE"
		mrg.copyToRemoteSFTP(localFile:"$rootDir/build/libs/someapp.war", remoteFile:"/websites/someapp.war")

	}
	

	task deployToServerAndFix << {
		
		MrGadget mrg = new MrGadget(
			user:'someuser', 
			host:'www.someuser.com', 
			strictHostKeyChecking:false, 
			leaveSessionOpen:true)				// leave session open to avoid having to reconnect when executing
												// multiple successive commands
			
		logger.info "SENDING FILE"
		mrg.copyToRemoteSFTP(localFile:"$rootDir/build/libs/someapp.war", remoteFile:"/websites/someapp.war")
		
		logger.info "SETTING OWNER OF WAR"
		mrg.execRemoteSudo("chown tomcat6:tomcat6 /websites/someapp.war")
		
		logger.info "SETTING PERMISSION OF WAR"
		mrg.execRemoteSudo("chmod 775 /websites/someapp.war")
		
		logger.info "RESTARTING TOMCAT"
		mrg.execRemoteSudo("service tomcat6 restart")
					
		logger.info "FINISHED"
		mrg.closeSession()						// don't forget to close the session

	}

	task clearPasswords << {					
		MrGadget mrg = new MrGadget(
			user:'someuser',
			host:'www.someuser.com',
			clearAllPasswords:true)				// passwords can be wiped from system prefs
	}

