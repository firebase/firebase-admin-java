package com.google.firebase.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.rmi.RemoteException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TasksTest {

  private static final Object RESULT = new Object();
  private static final RemoteException EXCEPTION = new RemoteException();
  private static final int SCHEDULE_DELAY_MS = 50;
  private static final int TIMEOUT_MS = 200;

  @Rule
  public ExpectedException mExpectedException = ExpectedException.none();

  private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

  @Test
  public void testForResult() throws Exception {
    Task<Object> task = Tasks.forResult(RESULT);
    assertEquals(RESULT, task.getResult());
  }

  @Test
  public void testForResult_nullResult() throws Exception {
    Task<Void> task = Tasks.forResult(null);
    assertNull(task.getResult());
  }

  @Test
  public void testForException() {
    Task<Void> task = Tasks.forException(EXCEPTION);
    assertEquals(EXCEPTION, task.getException());
  }

  @Test(expected = NullPointerException.class)
  @SuppressWarnings("ConstantConditions")
  public void testForException_nullException() {
    Tasks.forException(null);
  }

  @Test
  public void testCall_nonNullResult() {
    Task<Object> task =
        Tasks.call(
            TaskExecutors.DIRECT,
            new Callable<Object>() {
              @Override
              public Object call() throws Exception {
                return RESULT;
              }
            });
    assertEquals(RESULT, task.getResult());
  }

  @Test
  public void testCall_nullResult() {
    Task<Void> task =
        Tasks.call(
            TaskExecutors.DIRECT,
            new Callable<Void>() {
              @Override
              public Void call() throws Exception {
                return null;
              }
            });
    assertNull(task.getResult());
  }

  @Test
  public void testCall_exception() {
    Task<Void> task =
        Tasks.call(
            TaskExecutors.DIRECT,
            new Callable<Void>() {
              @Override
              public Void call() throws Exception {
                throw EXCEPTION;
              }
            });
    assertEquals(EXCEPTION, task.getException());
  }

  @Test(expected = NullPointerException.class)
  public void testCall_nullCallable() {
    Tasks.call(null);
  }

  @Test(expected = NullPointerException.class)
  public void testCall_nullExecutor() {
    Tasks.call(
        null,
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            return null;
          }
        });
  }

  @Test
  public void testAwait() throws Exception {
    TaskCompletionSource<Object> completionSource = new TaskCompletionSource<>();
    scheduleResult(completionSource);
    assertEquals(
        RESULT, Tasks.await(completionSource.getTask(), TIMEOUT_MS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testAwait_noTimeout() throws Exception {
    TaskCompletionSource<Object> completionSource = new TaskCompletionSource<>();
    scheduleResult(completionSource);
    assertEquals(RESULT, Tasks.await(completionSource.getTask()));
  }

  @Test
  public void testAwait_exception() throws Exception {
    mExpectedException.expect(ExecutionException.class);
    mExpectedException.expectCause(Matchers.is(EXCEPTION));

    TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
    scheduleException(completionSource);

    Tasks.await(completionSource.getTask(), TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testAwait_noTimeoutException() throws Exception {
    mExpectedException.expect(ExecutionException.class);
    mExpectedException.expectCause(Matchers.is(EXCEPTION));

    TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
    scheduleException(completionSource);

    Tasks.await(completionSource.getTask());
  }

  @Test
  public void testAwait_alreadyFailed() throws Exception {
    mExpectedException.expect(ExecutionException.class);
    mExpectedException.expectCause(Matchers.is(EXCEPTION));

    Task<Object> task = Tasks.forException(EXCEPTION);
    Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testAwait_noTimeoutAlreadyFailed() throws Exception {
    mExpectedException.expect(ExecutionException.class);
    mExpectedException.expectCause(Matchers.is(EXCEPTION));

    Task<Object> task = Tasks.forException(EXCEPTION);
    Tasks.await(task);
  }

  @Test
  public void testAwait_alreadySucceeded() throws Exception {
    Task<Object> task = Tasks.forResult(RESULT);
    assertEquals(RESULT, Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testAwait_noTimeoutAlreadySucceeded() throws Exception {
    Task<Object> task = Tasks.forResult(RESULT);
    assertEquals(RESULT, Tasks.await(task));
  }

  @Test(expected = InterruptedException.class)
  public void testAwait_interrupted() throws Exception {
    Task<Void> task = new TaskImpl<>();
    scheduleInterrupt();
    Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test(expected = InterruptedException.class)
  public void testAwait_noTimeoutInterrupted() throws Exception {
    Task<Void> task = new TaskImpl<>();
    scheduleInterrupt();
    Tasks.await(task);
  }

  @Test(expected = TimeoutException.class)
  public void testAwait_timeout() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test(expected = TimeoutException.class)
  public void testWhenAll_notCompleted() throws Exception {
    Task<Void> task1 = new TaskImpl<>();
    Task<Void> task2 = new TaskImpl<>();
    Task<Void> task = Tasks.whenAll(task1, task2);
    Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test(expected = TimeoutException.class)
  public void testWhenAll_partiallyCompleted() throws Exception {
    Task<Object> task1 = Tasks.forResult(RESULT);
    Task<Void> task2 = new TaskImpl<>();
    Task<Void> task = Tasks.whenAll(task1, task2);
    Tasks.await(task, TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testWhenAll_completedFailure() throws Exception {
    mExpectedException.expect(ExecutionException.class);
    mExpectedException.expectCause(Matchers.any(ExecutionException.class));

    Task<Object> task1 = Tasks.forResult(RESULT);
    Task<Object> task2 = Tasks.forException(EXCEPTION);
    Task<Void> task = Tasks.whenAll(task1, task2);
    Tasks.await(task);
  }

  @Test
  public void testWhenAll_completedSuccess() throws Exception {
    Task<Object> task1 = Tasks.forResult(RESULT);
    Task<Object> task2 = Tasks.forResult(RESULT);
    Task<Void> task = Tasks.whenAll(task1, task2);
    assertNull(Tasks.await(task));
  }

  @Test
  public void testWhenAll_completedEmpty() throws Exception {
    Task<Void> task = Tasks.whenAll();
    assertNull(Tasks.await(task));
  }

  @Test(expected = NullPointerException.class)
  public void testWhenAll_nullOnInput() throws Exception {
    Task<Object> task = Tasks.forResult(RESULT);
    Tasks.whenAll(task, null, task);
  }

  private void scheduleResult(final TaskCompletionSource<Object> completionSource) {
    @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
        mExecutor.schedule(
            new Runnable() {
              @Override
              public void run() {
                completionSource.setResult(RESULT);
              }
            },
            SCHEDULE_DELAY_MS,
            TimeUnit.MILLISECONDS);
  }

  private void scheduleException(final TaskCompletionSource<?> completionSource) {
    @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
        mExecutor.schedule(
            new Runnable() {
              @Override
              public void run() {
                completionSource.setException(EXCEPTION);
              }
            },
            SCHEDULE_DELAY_MS,
            TimeUnit.MILLISECONDS);
  }

  private void scheduleInterrupt() {
    final Thread testThread = Thread.currentThread();
    @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
        mExecutor.schedule(
            new Runnable() {
              @Override
              public void run() {
                testThread.interrupt();
              }
            },
            SCHEDULE_DELAY_MS,
            TimeUnit.MILLISECONDS);
  }
}
