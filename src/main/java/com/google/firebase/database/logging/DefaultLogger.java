package com.google.firebase.database.logging;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultLogger implements Logger {

  private final Set<String> enabledComponents;
  private final Level minLevel;

  public DefaultLogger(Level level, List<String> enabledComponents) {
    if (enabledComponents != null) {
      this.enabledComponents = new HashSet<>(enabledComponents);
    } else {
      this.enabledComponents = null;
    }
    minLevel = level;
  }

  @Override
  public Level getLogLevel() {
    return this.minLevel;
  }

  @Override
  public void onLogMessage(Level level, String tag, String message, long msTimestamp) {
    if (shouldLog(level, tag)) {
      String toLog = buildLogMessage(level, tag, message, msTimestamp);
      switch (level) {
        case ERROR:
          error(tag, toLog);
          break;
        case WARN:
          warn(tag, toLog);
          break;
        case INFO:
          info(tag, toLog);
          break;
        case DEBUG:
          debug(tag, toLog);
          break;
        default:
          throw new RuntimeException("Should not reach here!");
      }
    }
  }

  protected String buildLogMessage(Level level, String tag, String message, long msTimestamp) {
    Date now = new Date(msTimestamp);
    return now.toString() + " " + "[" + level + "] " + tag + ": " + message;
  }

  protected void error(String tag, String toLog) {
    System.err.println(toLog);
  }

  protected void warn(String tag, String toLog) {
    System.out.println(toLog);
  }

  protected void info(String tag, String toLog) {
    System.out.println(toLog);
  }

  protected void debug(String tag, String toLog) {
    System.out.println(toLog);
  }

  protected boolean shouldLog(Level level, String tag) {
    return (level.ordinal() >= minLevel.ordinal()
        && (enabledComponents == null
            || level.ordinal() > Level.DEBUG.ordinal()
            || enabledComponents.contains(tag)));
  }
}
