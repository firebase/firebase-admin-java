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

package com.google.firebase.tasks;

import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** 
 * Standard {@link Executor} instances for use with {@link Task}.
 */
public class TaskExecutors {

  /** An Executor that uses a shared cached thread pool. */
  public static final Executor DEFAULT_THREAD_POOL;
  /** An Executor that uses the calling thread. */
  static final Executor DIRECT =
      new Executor() {
        @Override
        public void execute(@NonNull Runnable command) {
          command.run();
        }
      };

  static {
    if (GaeThreadFactory.isAvailable()) {
      DEFAULT_THREAD_POOL = GaeThreadFactory.DEFAULT_EXECUTOR;
    } else {
      DEFAULT_THREAD_POOL = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }
  }

  private TaskExecutors() {}
}
