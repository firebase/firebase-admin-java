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

import java.util.concurrent.ThreadFactory;

public final class ThreadConfig {

  private final ThreadInitializer threadInitializer;
  private final ThreadFactory threadFactory;

  public ThreadConfig(ThreadInitializer threadInitializer, ThreadFactory threadFactory) {
    this.threadInitializer = checkNotNull(threadInitializer);
    this.threadFactory = checkNotNull(threadFactory);
  }

  ThreadInitializer getInitializer() {
    return threadInitializer;
  }

  ThreadFactory getThreadFactory() {
    return threadFactory;
  }
}
