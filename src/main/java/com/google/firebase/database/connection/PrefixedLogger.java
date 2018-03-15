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

package com.google.firebase.database.connection;

import org.slf4j.Logger;

public class PrefixedLogger {

  private final Logger logger;
  private final String prefix;

  public PrefixedLogger(Logger logger, String prefix) {
    this.logger = logger;
    this.prefix = prefix;
  }

  public void debug(String msg, Object... args) {
    if (logger.isDebugEnabled()) {
      logger.debug(addPrefix(msg), args);
    }
  }

  public void debug(String msg, Throwable t) {
    if (logger.isDebugEnabled()) {
      logger.debug(addPrefix(msg), t);
    }
  }

  public void error(String msg, Throwable t) {
    logger.error(addPrefix(msg), t);
  }

  public void warn(String msg) {
    logger.warn(addPrefix(msg));
  }

  public void info(String msg) {
    logger.info(addPrefix(msg));
  }

  private String addPrefix(String msg) {
    return prefix + " " + msg;
  }
}
