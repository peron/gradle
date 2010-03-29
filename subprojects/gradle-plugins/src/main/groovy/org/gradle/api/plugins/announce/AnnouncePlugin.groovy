package org.gradle.api.plugins;


import org.gradle.api.Plugin
import org.gradle.api.Project
import sun.misc.BASE64Encoder

/**
 * This plugin allows to send announce messages to twitter. It adds a announce method to the project class, which
 * can be called from the build script.
 *
 * @author hackergarten
 */
class AnnouncePlugin implements Plugin<Project> {
  public void apply(final Project project) {
    project.metaClass.announce = {String msg, def type ->
      if (type == "twitter") {
        new Twitter("username", "password").send(project.name, msg)
      } else if (type == "notify-send") {
        new NotifySend().send(project.name, msg)
      } else if (type == "snarl") {
        new Snarl().send(project.name, msg)
      }
    }
  }
}
