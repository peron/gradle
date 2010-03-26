package org.gradle.api.plugins;


interface Announcer {
  void send(String title, String message); 
}
