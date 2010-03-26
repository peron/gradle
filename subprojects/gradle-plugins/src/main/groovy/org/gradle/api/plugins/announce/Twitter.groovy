package org.gradle.api.plugins.announce;

import org.gradle.api.Plugin
import org.gradle.api.Project
import sun.misc.BASE64Encoder

/**
 * This class allows users to send announce messages to twitter.
 *
 * @author hackergarten
 */
class Twitter implements Announcer {

  def userName
  def password
  
  Twitter(String u, String p) {
    userName = u
    password = p
  }
  
  public void send(String title, String message) {
    def connection = new URL("https://twitter.com/statuses/update.xml").openConnection()
    connection.doInput = true
    connection.doOutput = true
    connection.useCaches = false
    
    String encoded = new BASE64Encoder().encodeBuffer("$userName:$password".toString().bytes).trim()
    connection.setRequestProperty "Authorization", "Basic " + encoded

    OutputStreamWriter out = new OutputStreamWriter(connection.outputStream)
    out.write "status=" + URLEncoder.encode(message, "UTF-8")
    out.close()

//    connection.inputStream.eachLine {
//        println it
//    }
        
    connection.disconnect()
  }
}
