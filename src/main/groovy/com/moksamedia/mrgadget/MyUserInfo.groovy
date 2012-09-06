package com.moksamedia.mrgadget

import groovy.util.logging.Slf4j

import com.jcraft.jsch.UserInfo

@Slf4j
class MyUserInfo implements UserInfo {
		
	public String getPassword() { return passwd }
		
	public boolean promptYesNo(String str) {

		def console = System.console()
		String response = 'n'
		if (console) {
			response = console.readLine("> $str (y/n): ")
		} else {
			println "Cannot get console."
		}

		return response == 'y' || response == 'Y' || response == 'yes' || response == 'Yes'
	}
  
	String passwd
	
	public String getPassphrase(){ return null; }
	
	public boolean promptPassphrase(String message){ return true }
	
	public boolean promptPassword(String message){
		
		def console = System.console()
		String response = ''
		if (console) {
			response = new String(console.readPassword("> $message: "))
		} else {
			log.error "Cannot get console."
		}

		passwd=response.trim()
		return true

	}
	public void showMessage(String message){
		def console = System.console()
		String response = ''
		if (console) {
			response = console.readLine("> $message (press enter to continue): ")
		} else {
			log.error "Cannot get console."
		}
	}
	
  }
