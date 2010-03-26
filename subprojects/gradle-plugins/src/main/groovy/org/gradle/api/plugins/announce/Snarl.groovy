package org.gradle.api.plugins;

import java.net.InetAddress
import java.net.Socket

class Snarl implements Announcer {
	private static final float SNP_VERSION = 1.1f
	private static final String HEAD = "type=SNP#?version=" + SNP_VERSION

	public void send(String title, String message) {
		send("localhost", title, message)
	}
	
	public void send(Collection hosts, String title, String message) {
		hosts.each { host ->
			send(host, title, message)
		}
	}
	
	public void send(String host, String title, String message) {
		with(new Socket(InetAddress.getByName(host), 9887)) { sock ->
			with(new PrintWriter(sock.getOutputStream(), true)) { out ->
				out.println(formatMessage(title, message))
			}
		}
	}
	
	private String formatMessage(String title, String message) {
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
	
	private String formatProperty(String name, String value) {
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
