/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides a logging interface for Firebase implementations. */
// TODO(depoll): Remove this or replace logging internally.
public final class Log {

  private static final String PARENT_LOGGER_NAME = "com.google.firebase";
  private static final String LOG_PREFIX = PARENT_LOGGER_NAME + ".";
  private static final Logger PARENT_LOGGER = Logger.getLogger(PARENT_LOGGER_NAME);
  private static final Level WTF_LEVEL = new Level("WTF", 1100) {};

  private Log() {}

  /**
   * Logs a message. Log levels correspond as follows:
   *
   * <ul>
   *   <li>WTF -> Level.SEVERE
   *   <li>ERROR -> Level.SEVERE
   *   <li>WARN -> Level.WARNING
   *   <li>INFO -> Level.INFO
   *   <li>DEBUG -> Level.FINE
   *   <li>VERBOSE -> Level.FINER
   * </ul>
   */
  private static void log(Level level, String tag, String msg, Throwable thrown) {
    Logger.getLogger(LOG_PREFIX + tag).log(level, msg, thrown);
  }

  // CSOFF: MethodName
  public static void d(String tag, String msg) {
    log(Level.FINE, tag, msg, null);
  }

  public static void i(String tag, String msg) {
    log(Level.INFO, tag, msg, null);
  }

  public static void w(String tag, String msg) {
    log(Level.WARNING, tag, msg, null);
  }
  // CSON: MethodName

  public static void wtf(String tag, String msg, Throwable thrown) {
    log(WTF_LEVEL, tag, msg, thrown);
  }
}
