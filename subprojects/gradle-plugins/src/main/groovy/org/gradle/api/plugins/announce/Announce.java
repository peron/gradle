package org.gradle.api.plugins.announce;


interface Announcer {
  void send(String title, String message); 
}
