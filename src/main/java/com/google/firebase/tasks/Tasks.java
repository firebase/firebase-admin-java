package com.google.firebase.tasks;

import com.google.firebase.internal.GuardedBy;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Preconditions;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * /** {@link Task} utility methods.
 */
public final class Tasks {

  /**
   * Returns a completed Task with the specified result.
   */
  public static <TResult> Task<TResult> forResult(TResult result) {
    TaskImpl<TResult> task = new TaskImpl<>();
    task.setResult(result);
    return task;
  }

  /**
   * Returns a completed Task with the specified exception.
   */
  public static <TResult> Task<TResult> forException(@NonNull Exception e) {
    TaskImpl<TResult> task = new TaskImpl<>();
    task.setException(e);
    return task;
  }

  /**
   * Returns a Task that will be completed with the result of the specified Callable.
   *
   * <p>The Callable will be called on a shared thread pool.
   */
  public static <TResult> Task<TResult> call(@NonNull Callable<TResult> callable) {
    return call(TaskExecutors.DEFAULT_THREAD_POOL, callable);
  }

  /**
   * Returns a Task that will be completed with the result of the specified Callable.
   *
   * @param executor the Executor to use to call the Callable
   */
  public static <TResult> Task<TResult> call(
      @NonNull Executor executor, @NonNull final Callable<TResult> callable) {
    Preconditions.checkNotNull(executor, "Executor must not be null");
    Preconditions.checkNotNull(callable, "Callback must not be null");

    final TaskImpl<TResult> task = new TaskImpl<>();
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
  public static <TResult> TResult await(@NonNull Task<TResult> task)
      throws ExecutionException, InterruptedException {
    Preconditions.checkNotNull(task, "Task must not be null");

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
  public static <TResult> TResult await(
      @NonNull Task<TResult> task, long timeout, @NonNull TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    Preconditions.checkNotNull(task, "Task must not be null");
    Preconditions.checkNotNull(unit, "TimeUnit must not be null");

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

  private static <TResult> TResult getResultOrThrowExecutionException(Task<TResult> task)
      throws ExecutionException {
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

  interface CombinedListener extends OnSuccessListener<Object>, OnFailureListener {

  }

  private static final class AwaitListener implements CombinedListener {

    private final CountDownLatch mLatch = new CountDownLatch(1);

    @Override
    public void onSuccess(Object o) {
      mLatch.countDown();
    }

    @Override
    public void onFailure(@NonNull Exception e) {
      mLatch.countDown();
    }

    public void await() throws InterruptedException {
      mLatch.await();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return mLatch.await(timeout, unit);
    }
  }

  private static final class WhenAllListener implements CombinedListener {

    private final Object mLock = new Object();
    private final int mNumTasks;
    private final TaskImpl<Void> mTask;

    @GuardedBy("mLock")
    private int mSuccessCounter;

    @GuardedBy("mLock")
    private int mFailuresCounter;

    @GuardedBy("mLock")
    private Exception mException;

    public WhenAllListener(int taskCount, TaskImpl<Void> task) {
      mNumTasks = taskCount;
      mTask = task;
    }

    @Override
    public void onFailure(@NonNull Exception e) {
      synchronized (mLock) {
        mFailuresCounter++;
        mException = e;
        checkForCompletionLocked();
      }
    }

    @Override
    public void onSuccess(Object o) {
      synchronized (mLock) {
        mSuccessCounter++;
        checkForCompletionLocked();
      }
    }

    @GuardedBy("mLock")
    private void checkForCompletionLocked() {
      if (mSuccessCounter + mFailuresCounter == mNumTasks) {
        if (mException == null) {
          mTask.setResult(null);
        } else {
          mTask.setException(
              new ExecutionException(
                  mFailuresCounter + " out of " + mNumTasks + " underlying tasks failed",
                  mException));
        }
      }
    }
  }

  private Tasks() {
  }
}
