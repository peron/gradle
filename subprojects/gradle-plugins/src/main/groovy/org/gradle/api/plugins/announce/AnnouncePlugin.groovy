/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.announce;


import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This plugin allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class AnnouncePlugin implements Plugin<Project> {

  public void apply(final Project project) {
    project.convention.plugins.announce = new AnnouncePluginConvention()

    project.metaClass.announce = {String msg, def type ->
      if (type == "twitter") {
        String username = project.announceTwitterUsername
        String password = project.announceTwitterPassword
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

