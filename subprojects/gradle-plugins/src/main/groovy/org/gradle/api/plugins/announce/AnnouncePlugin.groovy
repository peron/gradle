package org.gradle.api.plugins;


import org.gradle.api.Plugin
import org.gradle.api.Project
import sun.misc.BASE64Encoder

/**
 * This plugin allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class AnnouncePlugin implements Plugin<Project> {
  public void apply(final Project target) {
    target.metaClass.announce = {String msg, def type ->
      updateStatus("username", "password", msg)
    }
  }

  static String updateStatus(userName, password, message) {
    def connection = new URL("https://twitter.com/statuses/update.xml").openConnection()
    connection.doInput = true
    connection.doOutput = true
    connection.useCaches = false

    String encoded = new BASE64Encoder().encodeBuffer("$userName:$password".toString().bytes).trim()
    connection.setRequestProperty "Authorization", "Basic " + encoded

    OutputStreamWriter out = new OutputStreamWriter(connection.outputStream)
    out.write "status=" + URLEncoder.encode(message, "UTF-8")
    out.close()

    def result = ''
    connection.inputStream.eachLine { result += it }
    connection.disconnect()
    return result
  }
}