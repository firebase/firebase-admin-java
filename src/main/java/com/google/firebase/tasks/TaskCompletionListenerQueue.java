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

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

/**
 * A queue of listeners to call upon {@link Task} completion.
 *
 * @param <T> Task result type.
 */
class TaskCompletionListenerQueue<T> {

  private final Object lock = new Object();

  /** Lazily initialized, unbounded queue of listeners to call. */
  @GuardedBy("lock")
  private Queue<TaskCompletionListener<T>> queue;

  /**
   * Indicates if a flush is already in progress. While this is true, further calls to flush() will
   * do nothing.
   */
  @GuardedBy("lock")
  private boolean flushing;

  // TODO: Define behaviour for duplicate listeners.
  public void add(@NonNull TaskCompletionListener<T> listener) {
    synchronized (lock) {
      if (queue == null) {
        queue = new ArrayDeque<>();
      }
      queue.add(listener);
    }
  }

  public boolean removeAll(@NonNull Collection<TaskCompletionListener<T>> listeners) {
    synchronized (lock) {
      return queue == null || queue.removeAll(listeners);
    }
  }

  public void flush(@NonNull Task<T> task) {
    synchronized (lock) {
      if (queue == null || flushing) {
        return;
      }
      flushing = true;
    }

    while (true) {
      TaskCompletionListener<T> next;
      synchronized (lock) {
        next = queue.poll();
        if (next == null) {
          flushing = false;
          return;
        }
      }

      // Call outside the lock to avoid potential deadlocks with client code.
      next.onComplete(task);
    }
  }
}
