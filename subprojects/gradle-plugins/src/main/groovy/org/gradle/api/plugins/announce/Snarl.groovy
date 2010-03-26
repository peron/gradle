package org.gradle.plugins

import java.net.InetAddress
import java.net.Socket

class Snarl {
	private static final float SNP_VERSION = 1.1f
	private static final String HEAD = "type=SNP#?version=" + SNP_VERSION
	
	def send(Collection hosts, title, message) {
		hosts.each { host ->
			send(host, title, message)
		}
	}
	
	def send(host, title, message) {
		with(new Socket(InetAddress.getByName(host), 9887)) { sock ->
			with(new PrintWriter(sock.getOutputStream(), true)) { out ->
				out.println(formatMessage(title, message))
			}
		}
	}
	
	private formatMessage(title, message) {
		def properties = [
		        formatProperty("action", "notification"), 
				formatProperty("app", "Gradle Snarl Notifier"),
				formatProperty("class", "alert"),
				formatProperty("title", title),
				formatProperty("text", message),
				formatProperty("icon", null),
				formatProperty("timeout", "10") ]
		
		HEAD + properties.join('') + "\r\n"
	}
	
	private formatProperty(name, value) {
		if (!value) return ""
		else return "#?" + name + "=" + value
	}
	
	private with(closable, closure) {
		try {
			closure(closable)
		} finally {
			try {
				closable.close()
			} catch (Exception e) {}
		}
	}
}
