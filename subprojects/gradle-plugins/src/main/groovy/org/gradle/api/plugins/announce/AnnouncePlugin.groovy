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
      if (type == "twitter") {
        new Twitter("username", "password").send(target.name, msg)
      } else if (type == "notify-send") {
        new NotifySend().send(target.name, msg)
      } else if (type == "snarl") {
        new Snarl().send(target.name, msg)
      }
    }
  }
}
