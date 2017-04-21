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

package com.google.firebase.database.core;

import com.google.firebase.database.connection.PersistentConnection;
import java.lang.Thread.UncaughtExceptionHandler;

public class CoreTestHelpers {

  public static void freezeContext(Context context) {
    context.freeze();
  }

  public static PersistentConnection getRepoConnection(Repo repo) {
    return repo.getConnection();
  }

  public static void setEventTargetExceptionHandler(Context context,
      UncaughtExceptionHandler handler) {
    ThreadPoolEventTarget eventTarget = (ThreadPoolEventTarget) context.getEventTarget();
    eventTarget.setExceptionHandler(handler);
  }

  public static UncaughtExceptionHandler getEventTargetExceptionHandler(Context context) {
    ThreadPoolEventTarget eventTarget = (ThreadPoolEventTarget) context.getEventTarget();
    return eventTarget.getExceptionHandler();
  }
}
