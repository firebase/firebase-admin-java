package com.google.firebase.database.logging;

/**
 * Private (internal) logging interface used by Firebase Database.
 * See {@link com.google.firebase.database.Config Config} for more information.
 */
public interface Logger {

  /**
   * The log levels used by the Firebase Database library
   */
  enum Level {
    DEBUG, INFO, WARN, ERROR, NONE
  }

  /**
   * This method will be triggered whenever the library has something to log
   *
   * @param level The level of the log message
   * @param tag The component that this log message is coming from
   * @param message The message to be logged
   * @param msTimestamp The timestamp, in milliseconds, at which this message was generated
   */
  void onLogMessage(Level level, String tag, String message, long msTimestamp);

  Level getLogLevel();
}
