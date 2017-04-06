package com.google.firebase.database;

/**
 * This interface is used to setup logging for Firebase Database.
 */
public interface Logger {

  /**
   * The log levels used by the Firebase Database library
   */
  enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
  }

}
