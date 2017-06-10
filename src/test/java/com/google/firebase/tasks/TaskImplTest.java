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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.testing.TestOnCompleteListener;
import com.google.firebase.tasks.testing.TestOnFailureListener;
import com.google.firebase.tasks.testing.TestOnSuccessListener;
import java.rmi.RemoteException;
import org.junit.Test;

public class TaskImplTest {

  private static final Exception EXCEPTION = new RemoteException();
  private static final Void NULL_RESULT = null;
  private static final String NON_NULL_RESULT = "Success";

  @Test
  public void testIsComplete_notComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    assertFalse(task.isComplete());
  }

  @Test
  public void testIsComplete_failed() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);
    assertTrue(task.isComplete());
  }

  @Test
  public void testIsComplete_nullResult() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    assertTrue(task.isComplete());
  }

  @Test
  public void testIsComplete_nonNullResult() {
    TaskImpl<String> task = new TaskImpl<>();
    task.setResult(NON_NULL_RESULT);
    assertTrue(task.isComplete());
  }

  @Test
  public void testIsSuccessful_notComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    assertFalse(task.isSuccessful());
  }

  @Test
  public void testIsSuccessful_failed() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);
    assertFalse(task.isSuccessful());
  }

  @Test
  public void testIsSuccessful_nullResult() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    assertTrue(task.isSuccessful());
  }

  @Test
  public void testIsSuccessful_nonNullResult() {
    TaskImpl<String> task = new TaskImpl<>();
    task.setResult(NON_NULL_RESULT);
    assertTrue(task.isSuccessful());
  }

  @Test(expected = IllegalStateException.class)
  public void testGetResult_notComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.getResult();
  }

  @Test
  public void testGetResult_nullResult() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    assertNull(task.getResult());
  }

  @Test
  public void testGetResult_nonNullResult() {
    TaskImpl<String> task = new TaskImpl<>();
    task.setResult(NON_NULL_RESULT);
    assertEquals(NON_NULL_RESULT, task.getResult());
  }

  @Test
  public void testTrySetResult_nullResult() {
    TaskImpl<Void> task = new TaskImpl<>();
    assertTrue(task.trySetResult(NULL_RESULT));
    assertNull(task.getResult());
  }

  @Test
  public void testTrySetResult_nonNullResult() {
    TaskImpl<String> task = new TaskImpl<>();
    assertTrue(task.trySetResult(NON_NULL_RESULT));
    assertEquals(NON_NULL_RESULT, task.getResult());
  }

  @Test
  public void testGetResult_failure() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);

    try {
      task.getResult();
      fail("No exception thrown");
    } catch (RuntimeExecutionException e) {
      assertSame(EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testGetResult_exceptionIsSpecifiedType() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);

    try {
      task.getResult(RemoteException.class);
      fail("No exception thrown");
    } catch (RemoteException e) {
      assertSame(EXCEPTION, e);
    }
  }

  @Test
  public void testGetResult_exceptionIsNotSpecifiedType() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    Exception exception = new RuntimeException();
    task.setException(exception);

    try {
      task.getResult(RemoteException.class);
      fail("No exception thrown");
    } catch (RuntimeExecutionException e) {
      assertSame(exception, e.getCause());
    }
  }

  @Test
  public void testGetException_notComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    assertNull(task.getException());
  }

  @Test
  public void testGetException_failure() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);
    assertEquals(EXCEPTION, task.getException());
  }

  @Test
  public void testTrySetException() {
    TaskImpl<Void> task = new TaskImpl<>();
    assertTrue(task.trySetException(EXCEPTION));
    assertEquals(EXCEPTION, task.getException());
  }

  @Test
  public void testGetException_nullResult() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    assertNull(task.getException());
  }

  @Test
  public void testGetException_nonNullResult() {
    TaskImpl<String> task = new TaskImpl<>();
    task.setResult(NON_NULL_RESULT);
    assertNull(task.getException());
  }

  @Test
  public void testOnSuccess_nullResult() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    TestOnSuccessListener<Void> listener = new TestOnSuccessListener<>();
    task.addOnSuccessListener(listener);

    task.setResult(NULL_RESULT);

    assertTrue(listener.await());
    assertNull(listener.getResult());
  }

  @Test
  public void testOnSuccess_nonNullResult() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    TestOnSuccessListener<String> listener = new TestOnSuccessListener<>();
    task.addOnSuccessListener(listener);

    task.setResult(NON_NULL_RESULT);

    assertTrue(listener.await());
    assertEquals(NON_NULL_RESULT, listener.getResult());
  }

  @Test
  public void testOnSuccess_alreadyComplete() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    TestOnSuccessListener<String> listener = new TestOnSuccessListener<>();
    task.setResult(NON_NULL_RESULT);

    task.addOnSuccessListener(listener);

    assertTrue(listener.await());
    assertEquals(NON_NULL_RESULT, listener.getResult());
  }

  @Test
  public void testOnSuccess_acceptsBaseResult() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    TestOnSuccessListener<Object> listener = new TestOnSuccessListener<>();
    task.setResult(NON_NULL_RESULT);

    task.addOnSuccessListener(listener);

    assertTrue(listener.await());
    assertEquals(NON_NULL_RESULT, listener.getResult());
  }

  @Test
  public void testOnFailure() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    TestOnFailureListener listener = new TestOnFailureListener();
    task.addOnFailureListener(listener);

    task.setException(EXCEPTION);

    assertTrue(listener.await());
    assertEquals(EXCEPTION, listener.getException());
  }

  @Test
  public void testOnFailure_alreadyComplete() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    TestOnFailureListener listener = new TestOnFailureListener();
    task.setException(EXCEPTION);

    task.addOnFailureListener(listener);

    assertTrue(listener.await());
    assertEquals(EXCEPTION, listener.getException());
  }

  @Test
  public void testOnComplete_nullResult() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    TestOnCompleteListener<Void> listener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(listener);

    task.setResult(NULL_RESULT);

    assertTrue(listener.await());
    assertEquals(task, listener.getTask());
  }

  @Test
  public void testOnComplete_nonNullResult() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    TestOnCompleteListener<String> listener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(listener);

    task.setResult(NON_NULL_RESULT);

    assertTrue(listener.await());
    assertEquals(task, listener.getTask());
  }

  @Test
  public void testOnComplete_failure() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    TestOnCompleteListener<String> listener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(listener);

    task.setException(EXCEPTION);

    assertTrue(listener.await());
    assertEquals(task, listener.getTask());
  }

  @Test
  public void testOnComplete_alreadySucceeded() throws Exception {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);

    TestOnCompleteListener<Void> listener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(listener);

    assertTrue(listener.await());
    assertEquals(task, listener.getTask());
  }

  @Test
  public void testOnComplete_alreadyFailed() throws Exception {
    TaskImpl<String> task = new TaskImpl<>();
    task.setException(EXCEPTION);

    TestOnCompleteListener<String> listener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(listener);

    assertTrue(listener.await());
    assertEquals(task, listener.getTask());
  }

  @Test
  public void testContinueWith() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<String> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<Void, String>() {
              @Override
              public String then(@NonNull Task<Void> task) throws Exception {
                assertNull(task.getResult());
                return NON_NULL_RESULT;
              }
            });
    task.setResult(null);
    assertEquals(NON_NULL_RESULT, task2.getResult());
  }

  @Test
  public void testContinueWith_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    Task<Object> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<Void, Object>() {
              @Override
              public Object then(@NonNull Task<Void> task) throws Exception {
                assertNull(task.getResult());
                return NON_NULL_RESULT;
              }
            });
    assertEquals(NON_NULL_RESULT, task2.getResult());
  }

  @Test
  public void testContinueWith_propagatesException() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<Void, Void>() {
              @Override
              public Void then(@NonNull Task<Void> task) throws Exception {
                task.getResult();
                throw new AssertionError("Expected getResult to throw");
              }
            });
    task.setException(EXCEPTION);
    assertEquals(EXCEPTION, task2.getException());
  }

  @Test
  public void testContinueWith_continuationThrows() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<Void, Void>() {
              @Override
              public Void then(@NonNull Task<Void> task) throws Exception {
                throw EXCEPTION;
              }
            });
    task.setResult(null);
    assertEquals(EXCEPTION, task2.getException());
  }

  @Test
  public void testContinueWith_continuationThrowsWrapperWithoutCause() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<Void, Void>() {
              @Override
              public Void then(@NonNull Task<Void> task) throws Exception {
                throw new RuntimeExecutionException(null);
              }
            });
    task.setResult(null);
    assertTrue(task2.getException() instanceof RuntimeExecutionException);
  }

  @Test
  public void testContinueWith_continuationReturnsNull() {
    TaskImpl<String> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWith(
            TaskExecutors.DIRECT,
            new Continuation<String, Void>() {
              @Override
              public Void then(@NonNull Task<String> task) throws Exception {
                assertEquals(NON_NULL_RESULT, task.getResult());
                return null;
              }
            });
    task.setResult(NON_NULL_RESULT);
    assertNull(task2.getResult());
  }

  @Test
  public void testContinueWithTask() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<String> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<String>>() {
              @Override
              public Task<String> then(@NonNull Task<Void> task) throws Exception {
                assertNull(task.getResult());
                return Tasks.forResult(NON_NULL_RESULT);
              }
            });
    task.setResult(null);
    assertEquals(NON_NULL_RESULT, task2.getResult());
  }

  @Test
  public void testContinueWithTask_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    Task<String> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<String>>() {
              @Override
              public Task<String> then(@NonNull Task<Void> task) throws Exception {
                assertNull(task.getResult());
                return Tasks.forResult(NON_NULL_RESULT);
              }
            });
    assertEquals(NON_NULL_RESULT, task2.getResult());
  }

  @Test
  public void testContinueWithTask_propagatesException() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(@NonNull Task<Void> task) throws Exception {
                task.getResult();
                throw new AssertionError("Expected getResult to throw");
              }
            });
    task.setException(EXCEPTION);
    assertEquals(EXCEPTION, task2.getException());
  }

  @Test
  public void testContinueWithTask_continuationThrows() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(@NonNull Task<Void> task) throws Exception {
                throw EXCEPTION;
              }
            });
    task.setResult(null);
    assertEquals(EXCEPTION, task2.getException());
  }

  @Test
  public void testContinueWithTask_continuationThrowsWrapperWithoutCause() {
    TaskImpl<Void> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<Void>>() {
              @Override
              public Task<Void> then(@NonNull Task<Void> task) throws Exception {
                throw new RuntimeExecutionException(null);
              }
            });
    task.setResult(null);
    assertTrue(task2.getException() instanceof RuntimeExecutionException);
  }

  @Test
  public void testContinueWithTask_continuationReturnsIncompleteTask() {
    TaskImpl<Void> task = new TaskImpl<>();
    final TaskImpl<String> task2 = new TaskImpl<>();
    Task<String> task3 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<Void, Task<String>>() {
              @Override
              public Task<String> then(@NonNull Task<Void> task) throws Exception {
                return task2;
              }
            });
    task.setResult(NULL_RESULT);
    assertFalse(task3.isComplete());

    task2.setResult(NON_NULL_RESULT);
    assertEquals(NON_NULL_RESULT, task3.getResult());
  }

  @Test
  public void testContinueWithTask_continuationReturnsOriginalTask() {
    TaskImpl<String> task = new TaskImpl<>();
    Task<String> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<String, Task<String>>() {
              @Override
              public Task<String> then(@NonNull Task<String> task) throws Exception {
                return task;
              }
            });
    task.setResult(NON_NULL_RESULT);
    assertEquals(NON_NULL_RESULT, task2.getResult());
  }

  @Test
  public void testContinueWithTask_continuationReturnsNull() {
    TaskImpl<String> task = new TaskImpl<>();
    Task<Void> task2 =
        task.continueWithTask(
            TaskExecutors.DIRECT,
            new Continuation<String, Task<Void>>() {
              @Override
              public Task<Void> then(@NonNull Task<String> task) throws Exception {
                assertEquals(NON_NULL_RESULT, task.getResult());
                return null;
              }
            });
    task.setResult(NON_NULL_RESULT);
    assertTrue(task2.getException() instanceof NullPointerException);
  }

  @Test(expected = IllegalStateException.class)
  public void testSetResult_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);
    task.setResult(NULL_RESULT);
  }

  @Test
  public void testTrySetResult_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(EXCEPTION);
    // Expect no exception to be thrown.
    assertFalse(task.trySetResult(NULL_RESULT));
    assertEquals(EXCEPTION, task.getException());
  }

  @Test(expected = IllegalStateException.class)
  public void testSetException_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    task.setException(EXCEPTION);
  }

  @Test
  public void testTrySetException_alreadyComplete() {
    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(NULL_RESULT);
    // Expect no exception to be thrown.
    assertFalse(task.trySetException(EXCEPTION));
    assertNull(task.getResult());
  }
}
