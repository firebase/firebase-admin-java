/*
 * Copyright 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * A single-threaded scheduled executor implementation. Allows naming the threads, and spawns
 * new threads as daemons.
 */
public class FirebaseScheduledExecutor extends ScheduledThreadPoolExecutor {

  public FirebaseScheduledExecutor(@NonNull ThreadFactory threadFactory, @NonNull String name) {
    this(threadFactory, name, null);
  }

  public FirebaseScheduledExecutor(
      @NonNull ThreadFactory threadFactory, @NonNull String name,
      @Nullable Thread.UncaughtExceptionHandler handler) {
    super(1, decorateThreadFactory(threadFactory, name, handler));
    setRemoveOnCancelPolicy(true);
  }

  static ThreadFactory getThreadFactoryWithName(
      @NonNull ThreadFactory threadFactory, @NonNull String name) {
    return decorateThreadFactory(threadFactory, name, null);
  }

  private static ThreadFactory decorateThreadFactory(
      ThreadFactory threadFactory, String name, Thread.UncaughtExceptionHandler handler) {
    checkArgument(!Strings.isNullOrEmpty(name));
    ThreadFactoryBuilder builder = new ThreadFactoryBuilder()
        .setThreadFactory(threadFactory)
        .setNameFormat(name)
        .setDaemon(true);
    if (handler != null) {
      builder.setUncaughtExceptionHandler(handler);
    }
    return builder.build();
  }
}
