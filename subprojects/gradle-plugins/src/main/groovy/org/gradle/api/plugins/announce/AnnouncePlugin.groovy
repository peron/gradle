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
  public void apply(final Project project) {

    project.convention.plugins.announce = new AnnouncePluginConvention()

    project.metaClass.announce = {String msg, def type ->

      def username = project.convention.plugins.announce.username
      def password = project.convention.plugins.announce.password

      if (type == "twitter") {
        new Twitter(username, password).send(project.name, msg)
      } else if (type == "notify-send") {
        new NotifySend().send(project.name, msg)
      } else if (type == "snarl") {
        new Snarl().send(project.name, msg)
      }
    }
  }
}

class AnnouncePluginConvention {
    String username
    String password
    
    def announce(Closure closure) {
        closure.delegate = this
        closure() 
    }
}

