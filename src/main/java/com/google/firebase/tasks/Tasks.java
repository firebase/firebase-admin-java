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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 
 * {@link Task} utility methods.
 *
 * @deprecated Use ApiFutures and ThreadManager interface instead.
 */
public final class Tasks {

  private Tasks() {}

  /** Returns a completed Task with the specified result. */
  public static <T> Task<T> forResult(T result) {
    TaskImpl<T> task = new TaskImpl<>();
    task.setResult(result);
    return task;
  }

  /** Returns a completed Task with the specified exception. */
  public static <T> Task<T> forException(@NonNull Exception exception) {
    TaskImpl<T> task = new TaskImpl<>();
    task.setException(exception);
    return task;
  }

  /**
   * Returns a Task that will be completed with the result of the specified Callable.
   *
   * <p>The Callable will be called on a shared thread pool.
   *
   * @deprecated Use {@link #call(Executor, Callable)}
   */
  public static <T> Task<T> call(@NonNull Callable<T> callable) {
    return call(TaskExecutors.DEFAULT_THREAD_POOL, callable);
  }

  /**
   * Returns a Task that will be completed with the result of the specified Callable.
   *
   * @param executor the Executor to use to call the Callable
   */
  public static <T> Task<T> call(@NonNull Executor executor, @NonNull final Callable<T> callable) {
    checkNotNull(executor, "Executor must not be null");
    checkNotNull(callable, "Callback must not be null");

    final TaskImpl<T> task = new TaskImpl<>();
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              task.setResult(callable.call());
            } catch (Exception e) {
              task.setException(e);
            }
          }
        });
    return task;
  }

  /**
   * Blocks until the specified Task is complete.
   *
   * @return the Task's result
   * @throws ExecutionException if the Task fails
   * @throws InterruptedException if an interrupt occurs while waiting for the Task to complete
   */
  public static <T> T await(@NonNull Task<T> task) throws ExecutionException, InterruptedException {
    checkNotNull(task, "Task must not be null");

    if (task.isComplete()) {
      return getResultOrThrowExecutionException(task);
    }

    AwaitListener listener = new AwaitListener();
    addListener(task, listener);
    listener.await();

    return getResultOrThrowExecutionException(task);
  }

  /**
   * Blocks until the specified Task is complete.
   *
   * @return the Task's result
   * @throws ExecutionException if the Task fails
   * @throws InterruptedException if an interrupt occurs while waiting for the Task to complete
   * @throws TimeoutException if the specified timeout is reached before the Task completes
   */
  public static <T> T await(@NonNull Task<T> task, long timeout, @NonNull TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    checkNotNull(task, "Task must not be null");
    checkNotNull(unit, "TimeUnit must not be null");

    if (task.isComplete()) {
      return getResultOrThrowExecutionException(task);
    }

    AwaitListener listener = new AwaitListener();
    addListener(task, listener);

    if (!listener.await(timeout, unit)) {
      throw new TimeoutException("Timed out waiting for Task");
    }

    return getResultOrThrowExecutionException(task);
  }

  /**
   * Returns a Task that completes successfully when all of the specified Tasks complete
   * successfully. Does not accept nulls.
   *
   * @throws NullPointerException if any of the provided Tasks are null
   */
  public static Task<Void> whenAll(final Collection<? extends Task<?>> tasks) {
    if (tasks.isEmpty()) {
      return Tasks.forResult(null);
    }
    for (Task<?> task : tasks) {
      if (task == null) {
        throw new NullPointerException("null tasks are not accepted");
      }
    }
    TaskImpl<Void> whenAllTask = new TaskImpl<>();
    WhenAllListener listener = new WhenAllListener(tasks.size(), whenAllTask);
    for (Task<?> task : tasks) {
      addListener(task, listener);
    }
    return whenAllTask;
  }

  /**
   * Returns a Task that completes successfully when all of the specified Tasks complete
   * successfully. Does not accept nulls.
   *
   * @throws NullPointerException if any of the provided Tasks are null
   */
  public static Task<Void> whenAll(Task<?>... tasks) {
    if (tasks.length == 0) {
      return Tasks.forResult(null);
    }
    return whenAll(Arrays.asList(tasks));
  }

  private static <T> T getResultOrThrowExecutionException(Task<T> task) throws ExecutionException {
    if (task.isSuccessful()) {
      return task.getResult();
    } else {
      throw new ExecutionException(task.getException());
    }
  }

  private static void addListener(Task<?> task, CombinedListener listener) {
    // Use a direct executor to avoid an additional thread-hop.
    task.addOnSuccessListener(TaskExecutors.DIRECT, listener);
    task.addOnFailureListener(TaskExecutors.DIRECT, listener);
  }

  interface CombinedListener extends OnSuccessListener<Object>, OnFailureListener {}

  private static final class AwaitListener implements CombinedListener {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSuccess(Object obj) {
      latch.countDown();
    }

    @Override
    public void onFailure(@NonNull Exception exception) {
      latch.countDown();
    }

    public void await() throws InterruptedException {
      latch.await();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return latch.await(timeout, unit);
    }
  }

  private static final class WhenAllListener implements CombinedListener {

    private final Object lock = new Object();
    private final int numTasks;
    private final TaskImpl<Void> task;

    @GuardedBy("lock")
    private int successCounter;

    @GuardedBy("lock")
    private int failuresCounter;

    @GuardedBy("lock")
    private Exception exception;

    public WhenAllListener(int taskCount, TaskImpl<Void> task) {
      numTasks = taskCount;
      this.task = task;
    }

    @Override
    public void onFailure(@NonNull Exception exception) {
      synchronized (lock) {
        failuresCounter++;
        this.exception = exception;
        checkForCompletionLocked();
      }
    }

    @Override
    public void onSuccess(Object obj) {
      synchronized (lock) {
        successCounter++;
        checkForCompletionLocked();
      }
    }

    @GuardedBy("lock")
    private void checkForCompletionLocked() {
      if (successCounter + failuresCounter == numTasks) {
        if (exception == null) {
          task.setResult(null);
        } else {
          task.setException(
              new ExecutionException(
                  failuresCounter + " out of " + numTasks + " underlying tasks failed", exception));
        }
      }
    }
  }
}
