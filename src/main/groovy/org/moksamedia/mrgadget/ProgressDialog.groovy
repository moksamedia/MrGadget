package org.moksamedia.mrgadget

import java.awt.BorderLayout
import java.awt.Font
import java.text.DecimalFormat

import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.border.EmptyBorder

// Progress bar dialog optionally used when sending files remotely
class ProgressDialog {
	
	JDialog dlg
	JProgressBar dpb
	
	Date start = new Date()
	float elapsedTimeMin
	float elapsedTimeMillis
	float minRemaining
	
	DecimalFormat decFormat
	
	String toHost, toFile
	long fileSize
	
	JLabel labelMinRemaining
	JPanel southPanel

	public ProgressDialog(String fromFileName, String toHost, String toFile, long fileSize) {
		
		this.toHost = toHost
		this.toFile = toFile
		this.fileSize = fileSize
		
		decFormat = new DecimalFormat("#,##0.00")
		
		dlg = new JDialog()
		JPanel panel = new JPanel()
		panel.setBorder(new EmptyBorder(20,20,20,20))
		panel.setLayout(new BorderLayout())
		dpb = new JProgressBar(0, Math.round(fileSize))
		String readableSize = MrGadget.humanReadableByteCount(fileSize)
		JLabel label = new JLabel("Sending file '${fromFileName}' - $readableSize")
		southPanel = new JPanel()
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS))
		southPanel.add(new JLabel("To: ${toHost}${toFile}"))
		labelMinRemaining = new JLabel("Est. min. remaining: ")
		southPanel.add(labelMinRemaining)
		label.setFont(new Font("Dialog", 1, 14))
		panel.add(BorderLayout.NORTH, label)
		panel.add(BorderLayout.CENTER, dpb)
		panel.add(BorderLayout.SOUTH, southPanel)
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)
		dlg.setSize(450, 150)
		dlg.add(panel)
		dlg.setVisible(true)
		
	}
	
	public void setValue(long val) {
		setValue(Math.round(val))
	}
	
	public void setValue(int val) {
		
		elapsedTimeMillis = (new Date()).getTime() - start.getTime()
		elapsedTimeMin = elapsedTimeMillis/(60*1000F)
		minRemaining = elapsedTimeMin / (val / fileSize) - elapsedTimeMin
		
		labelMinRemaining.setText("Est. min. remaining: ${decFormat.format(minRemaining)}")
		
		dpb.setValue(val)
	}
	
	public setVisible(boolean val) {
		dlg.setVisible(val)
	}

}
