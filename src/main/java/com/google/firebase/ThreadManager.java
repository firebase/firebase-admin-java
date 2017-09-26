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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * An interface that controls the thread pools and thread factories used by the Admin SDK. Each
 * instance of {@link FirebaseApp} uses an implementation of this interface to create and manage
 * threads. Multiple app instances may use the same <code>ThreadManager</code> instance.
 * Methods in this interface may get invoked multiple times by the same
 * app, during its lifetime. Apps may also invoke methods of this interface concurrently, and
 * therefore implementations should provide any synchronization necessary.
 */
public abstract class ThreadManager {

  @NonNull
  final ListeningScheduledExecutorService getListeningExecutor(@NonNull FirebaseApp app) {
    ScheduledExecutorService executor = getExecutor(app);
    checkNotNull(executor, "ScheduledExecutorService must not be null");
    return MoreExecutors.listeningDecorator(executor);
  }

  /**
   * Returns the main thread pool for an app. Implementations may return the same instance of
   * <code>ScheduledExecutorService</code> for multiple apps. The returned thread pool is used by
   * all components of an app except for the Realtime Database. Database has far stricter and
   * complicated threading requirements, and thus initializes its own threads using the
   * factory returned by {@link ThreadManager#getThreadFactory()}.
   *
   * @param app A {@link FirebaseApp} instance.
   * @return A non-null {@link ScheduledExecutorService} instance.
   */
  @NonNull
  protected abstract ScheduledExecutorService getExecutor(@NonNull FirebaseApp app);

  /**
   * Cleans up the thread pool associated with an app. This method is invoked when an
   * app is deleted.
   *
   * @param app A {@link FirebaseApp} instance.
   */
  protected abstract void releaseExecutor(
      @NonNull FirebaseApp app, @NonNull ScheduledExecutorService executor);

  /**
   * Returns the <code>ThreadFactory</code> to be used for creating any additional threads
   * required by the SDK. This is used mainly to create the long-lived worker threads for
   * Realtime Database client.
   *
   * @return A non-null <code>ThreadFactory</code>.
   */
  @NonNull
  protected abstract ThreadFactory getThreadFactory();

}
