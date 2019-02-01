/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

public class LazyInitializer<T> {

  private final InitializerFunc<T> func;
  private final Object lock;
  private volatile ValueOrException<T> valueOrException;

  public LazyInitializer(InitializerFunc<T> func, @Nullable Object lock) {
    this.func = checkNotNull(func);
    this.lock = lock != null ? lock : new Object();
  }

  public T getValue() {
    if (valueOrException == null) {
      synchronized (lock) {
        if (valueOrException == null) {
          valueOrException = new ValueOrException<>(func);
        }
      }
    }
    return valueOrException.getOrThrow();
  }

  private static class ValueOrException<T> {
    private final T value;
    private final RuntimeException exception;

    ValueOrException(InitializerFunc<T> func) {
      T value = null;
      RuntimeException exception = null;
      try {
        value = func.invoke();
      } catch (RuntimeException e) {
        exception = e;
      }
      this.value = value;
      this.exception = exception;
    }

    T getOrThrow() {
      if (exception != null) {
        throw exception;
      }
      return value;
    }
  }
}
