package com.moksamedia.mrgadget

import groovy.util.logging.Slf4j

import java.text.DecimalFormat

import com.jcraft.jsch.SftpProgressMonitor

@Slf4j
protected class MrGadgetProgressMonitor implements SftpProgressMonitor {

	// constructor 
	public MrGadgetProgressMonitor(def params = [:]) {
		host = params.host 	// the remote host to which the file is being sent
		showProgressDialog = params.get('showProgressDialog', false) // should the Swing progress dialog box be shown
		logClosure = params.logClosure  // an optional closure to be called intermittently
		bytesToSend = params.bytesToSend  // how many bytes we're sending
		sourceFile = params.sourceFile  // the full path to the local file
		destinationFile = params.destinationFile  // the full path (including file name and extension) to the new remote file
		countClosureFrequency = params.get('countClosureFrequency', 10) // how often the logClosure is called, and how often the info is logged
	}
	
	// the remote host or ip
	String host
	
	// should we show the Swing dialog progress box
	boolean showProgressDialog // defaults to false
	ProgressDialog progressDialog
	
	String sourceFile
	String destinationFile
	
	long bytesToSend
	long bytesSent = 0
	
	// formatter used for logging
	DecimalFormat decFormat = new DecimalFormat("#,##0.00")
	
	// optional closure called every 'countClosureFrequency' percent of file sent
	Closure logClosure
	int countClosureFrequency // defaults to 10
	
	// used to track percent finished (and avoid calling multiple times for save rounded percent)
	int count = 0
	int percent = 0
	long lastReportedPercent = 0

	// track and calculate elapsed time
	Date startTimeMillis = new Date()
	float elapsedTimeMin
	float elapsedTimeMillis
	
	// estimated minutes remaining = minutes elapsed / percent complete
	float minRemaining

	// show dialog, if necessary
	public void showDialog() {
		if (!showProgressDialog) return
		log.info "Showing progress dialog. Closing dialog will NOT halt transfer, but closing Gradle process (if using gradle), will stop transfer."
		progressDialog = new ProgressDialog(sourceFile.split('/').last(), host, destinationFile, bytesToSend)
	}
	
	// SftpProgressMonitor init, used by SFTP operation only
	@Override
	public void init(int op, String src, String dest, long max) {
		sourceFile = src
		destinationFile = dest
		bytesToSend = max
	}

	// iteration method; count is bytes sent in the current iteration (not total bytes sent)
	@Override
	public boolean count(long count) {
				
		// calculate integer percent
		percent = Math.round((bytesSent / bytesToSend) * 100)

		// increment bytes sent by count bytes
		bytesSent += count
		
		// calculate elapsed time in milliseconds
		elapsedTimeMillis = (new Date()).getTime() - startTimeMillis.getTime()
		
		// calculate elapsed time in minutes
		elapsedTimeMin = elapsedTimeMillis/(60*1000F)
		
		// estimate time remaining, avoiding an unlikely divide by zero
		if (percent == 0) minRemaining = 0
		else minRemaining = elapsedTimeMin / (percent / 100) - elapsedTimeMin

		// increment count (used to only call log occasionally)
		count += 1

		/*
		 * Write to log every 'countClosureFrequency' percent, but because percent is rounded,
		 * we avoid making multiple calls for a strike by tracking the last reported percent
		 */
		if (percent % countClosureFrequency == 0 && percent != lastReportedPercent) {
			if (logClosure != null) {
				logClosure.call(bytesSent, bytesToSend, elapsedTimeMillis)
			}
			else {
				log.info "Sent ${percent}% - ${MrGadget.humanReadableByteCount(bytesSent)} of ${MrGadget.humanReadableByteCount(bytesToSend)} - ${decFormat.format(minRemaining)} min. remaining"
			}
			lastReportedPercent = percent
		}
		
		// update the dialog, if necessary
		if (showProgressDialog) progressDialog.setValue(bytesSent)
		
		// return true to continue sending file (false should cancel send)
		true
	}

	// finish up by hiding the dialog, if necessary
	@Override
	public void end() {
		if (showProgressDialog) progressDialog.setVisible(false)
	}

}
