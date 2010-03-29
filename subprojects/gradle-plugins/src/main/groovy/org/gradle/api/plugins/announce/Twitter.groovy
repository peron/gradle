package org.gradle.api.plugins.announce;


import org.gradle.api.logging.Logger
import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder

/**
 * This class allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class Twitter implements Announcer {

  private static final String TWITTER_UPDATE_URL = "https://twitter.com/statuses/update.xml"

  def userName
  def password

  private static Logger logger = LoggerFactory.getLogger(Twitter)

  Twitter(String username, String password) {
    this.userName = username
    this.password = password
  }

  public void send(String title, String message) {
    OutputStreamWriter out
    URL connection
    try {
      connection = new URL(TWITTER_UPDATE_URL).openConnection()
      connection.doInput = true
      connection.doOutput = true
      connection.useCaches = false
      String encoded = new BASE64Encoder().encodeBuffer("$userName:$password".toString().bytes).trim()
      connection.setRequestProperty "Authorization", "Basic " + encoded
      out = new OutputStreamWriter(connection.outputStream)
      out.write "status=" + URLEncoder.encode(message, "UTF-8")
    } catch (Exception e) {
       logger.error('Could not send message to twitter', e)
    } finally {
      out?.close()
      connection?.disconnect()

    }

  }
}
