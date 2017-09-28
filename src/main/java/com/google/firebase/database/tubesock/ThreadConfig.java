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

package com.google.firebase.database.tubesock;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.database.core.ThreadInitializer;

import com.google.firebase.internal.NonNull;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration that governs how the websocket connections spawn and initialize threads
 * for reading and writing data.
 */
public final class ThreadConfig {

  private final ThreadFactory threadFactory;
  private final ThreadInitializer threadInitializer;

  public ThreadConfig(ThreadFactory threadFactory, ThreadInitializer threadInitializer) {
    this.threadFactory = checkNotNull(threadFactory);
    this.threadInitializer = checkNotNull(threadInitializer);
  }

  /**
   * The <code>ThreadFactory</code> implementation to be used for creating (spawning) new
   * threads.
   *
   * @return a non-null <code>ThreadFactory</code> instance.
   */
  @NonNull
  ThreadFactory getThreadFactory() {
    return threadFactory;
  }

  /**
   * The {@link ThreadInitializer} implementation to be used for further configuring the threads
   * created by the <code>ThreadFactory</code>. In particular, this instance is used to rename
   * the created threads, and turn them into daemons if necessary.
   *
   * @return a non-null {@link ThreadInitializer} instance.
   */
  @NonNull
  ThreadInitializer getInitializer() {
    return threadInitializer;
  }
}
