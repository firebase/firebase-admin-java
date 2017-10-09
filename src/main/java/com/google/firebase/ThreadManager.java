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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.internal.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * An interface that controls the thread pools and thread factories used by the Admin SDK. Each
 * instance of {@link FirebaseApp} uses an implementation of this interface to create and manage
 * threads.
 *
 * <p>Multiple app instances may use the same <code>ThreadManager</code> instance.
 * Methods in this interface may get invoked multiple times by the same
 * app, during its lifetime. Apps may also invoke methods of this interface concurrently and
 * so implementations should provide any synchronization necessary.
 */
public abstract class ThreadManager {

  @NonNull
  final FirebaseExecutors getFirebaseExecutors(@NonNull FirebaseApp app) {
    return new FirebaseExecutors(getExecutor(app));
  }

  final void releaseFirebaseExecutors(
      @NonNull FirebaseApp app, @NonNull FirebaseExecutors executor) {
    releaseExecutor(app, executor.userExecutor);
  }

  /**
   * Returns the main thread pool for an app. Implementations may return the same instance of
   * <code>ExecutorService</code> for multiple apps. The returned thread pool is used for
   * short-lived tasks by all components of an app.
   *
   * <p>For long-lived tasks (such as the ones started by the Realtime Database client), the SDK
   * creates dedicated executors using the <code>ThreadFactory</code> returned by the
   * {@link #getThreadFactory()} method.
   *
   * @param app A {@link FirebaseApp} instance.
   * @return A non-null {@link ExecutorService} instance.
   */
  @NonNull
  protected abstract ExecutorService getExecutor(@NonNull FirebaseApp app);

  /**
   * Cleans up the thread pool associated with an app. This method is invoked when an app
   * is deleted. This is guaranteed to be called with the <code>ExecutorService</code> previously
   * returned by {@link #getExecutor(FirebaseApp)} for the corresponding app.
   *
   * @param app A {@link FirebaseApp} instance.
   */
  protected abstract void releaseExecutor(
      @NonNull FirebaseApp app, @NonNull ExecutorService executor);

  /**
   * Returns the <code>ThreadFactory</code> to be used for creating long-lived threads. This is
   * used mainly to create the long-lived worker threads for the Realtime Database client, and
   * other scheduled (periodic) tasks started by the SDK. The SDK guarantees
   * clean termination of all threads started via this <code>ThreadFactory</code>, when the user
   * calls {@link FirebaseApp#delete()}.
   *
   * <p>If long-lived threads cannot be supported in the current runtime, this method may
   * throw a {@code RuntimeException}.
   *
   * @return A non-null <code>ThreadFactory</code>.
   */
  @NonNull
  protected abstract ThreadFactory getThreadFactory();

  /**
   * Wraps an ExecutorService in a ListeningExecutorService while keeping a reference to the
   * original ExecutorService. This reference is used when it's time to release/cleanup the
   * original ExecutorService.
   */
  static final class FirebaseExecutors {
    private final ExecutorService userExecutor;
    private final ListeningExecutorService listeningExecutor;

    private FirebaseExecutors(ExecutorService userExecutor) {
      this.userExecutor = checkNotNull(userExecutor, "ExecutorService must not be null");
      this.listeningExecutor = MoreExecutors.listeningDecorator(userExecutor);
    }

    ListeningExecutorService getListeningExecutor() {
      return listeningExecutor;
    }
  }

}
