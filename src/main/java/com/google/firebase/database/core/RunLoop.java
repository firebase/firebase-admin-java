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

import java.util.concurrent.ScheduledFuture;

/**
 * This interface defines the required functionality for the Firebase Database library's run loop.
 * Most users will not need this interface. However, if you are customizing how the Firebase
 * Database schedules its internal operations this is the interface that should be implemented.
 */
@SuppressWarnings("rawtypes")
public interface RunLoop {

  /**
   * Append this operation to the queue.
   *
   * @param r The operation to run
   */
  void scheduleNow(Runnable r);

  /**
   * Schedule this operation to run after the specified delay.
   *
   * @param r The operation to run
   * @param milliseconds The delay, in milliseconds
   * @return A Future that can be used to cancel the operation if it has not yet started executing
   */
  ScheduledFuture schedule(Runnable r, long milliseconds);

  void shutdown();

  void restart();
}
