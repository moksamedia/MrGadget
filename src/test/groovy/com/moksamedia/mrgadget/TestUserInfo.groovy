package com.moksamedia.mrgadget

import groovy.lang.Closure;
import groovy.util.logging.Slf4j;

import com.jcraft.jsch.UserInfo;

@Slf4j
protected class TestUserInfo implements UserInfo {
	
	String passwd
	
	Closure getPassphraseClosure = { log.info "Getting passphrase"; null }
	Closure getPasswordClosure = { }
	Closure promptYesNoClosure = { String message -> log.info message }
	Closure promptPassphraseClosure = { String message -> log.info message }
	Closure promptPasswordClosure = { String message -> log.info message }
	Closure showMessageClosure = { String message -> log.info message }
		
	public String getPassword() { getPasswordClosure() }
		
	public boolean promptYesNo(String str) { promptYesNoClosure(str) }
	  
	public String getPassphrase(){ getPassphraseClosure() }
	
	public boolean promptPassphrase(String message){ promptPassphraseClosure(message) }
	
	public boolean promptPassword(String message){ promptPasswordClosure(message) }
	
	public void showMessage(String message){ promptMessageClosure(message) }
	
  }