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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import java.util.concurrent.Callable;

/**
 * An operation that can be invoked synchronously or asynchronously. Subclasses can specify
 * the return type and a specific exception type to be thrown.
 */
public abstract class CallableOperation<T, V extends Exception> implements Callable<T> {

  protected abstract T execute() throws V;

  @Override
  public final T call() throws V {
    return execute();
  }

  /**
   * Run this operation asynchronously on the main thread pool of the specified {@link FirebaseApp}.
   *
   * @param app A non-null {@link FirebaseApp}.
   * @return An {@code ApiFuture}.
   */
  public final ApiFuture<T> callAsync(@NonNull FirebaseApp app) {
    checkNotNull(app);
    return ImplFirebaseTrampolines.submitCallable(app, this);
  }
}
