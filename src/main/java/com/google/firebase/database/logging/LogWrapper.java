package com.google.firebase.database.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

/** User: greg Date: 6/12/13 Time: 2:23 PM */
public class LogWrapper {

  private final Logger logger;
  private final String component;
  private final String prefix;

  public LogWrapper(Logger logger, String component) {
    this(logger, component, null);
  }

  public LogWrapper(Logger logger, String component, String prefix) {
    this.logger = logger;
    this.component = component;
    this.prefix = prefix;
  }

  private static String exceptionStacktrace(Throwable e) {
    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    e.printStackTrace(printWriter);
    return writer.toString();
  }

  public void error(String message, Throwable e) {
    String logMsg = toLog(message) + "\n" + exceptionStacktrace(e);
    logger.onLogMessage(Logger.Level.ERROR, component, logMsg, now());
  }

  public void warn(String message) {
    warn(message, null);
  }

  public void warn(String message, Throwable e) {
    String logMsg = toLog(message);
    if (e != null) {
      logMsg = logMsg + "\n" + exceptionStacktrace(e);
    }
    logger.onLogMessage(Logger.Level.WARN, component, logMsg, now());
  }

  public void info(String message) {
    logger.onLogMessage(Logger.Level.INFO, component, toLog(message), now());
  }

  public void debug(String message, Object... args) {
    this.debug(message, null, args);
  }

  /** Log a non-fatal exception. Typically something like an IO error on a failed connection */
  public void debug(String message, Throwable e, Object... args) {
    if (this.logsDebug()) {
      String logMsg = toLog(message, args);
      if (e != null) {
        logMsg = logMsg + "\n" + exceptionStacktrace(e);
      }
      logger.onLogMessage(Logger.Level.DEBUG, component, logMsg, now());
    }
  }

  public boolean logsDebug() {
    return this.logger.getLogLevel().ordinal() <= Logger.Level.DEBUG.ordinal();
  }

  private long now() {
    return System.currentTimeMillis();
  }

  private String toLog(String message, Object... args) {
    String formatted = (args.length > 0) ? String.format(message, args) : message;
    return prefix == null ? formatted : prefix + " - " + formatted;
  }
}
