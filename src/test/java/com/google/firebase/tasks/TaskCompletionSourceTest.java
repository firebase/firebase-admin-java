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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.rmi.RemoteException;
import org.junit.Test;

public class TaskCompletionSourceTest {

  private static final String RESULT = "Success";
  private static final String RESULT_2 = "Success2";
  private static final RemoteException EXCEPTION = new RemoteException();
  private static final RemoteException EXCEPTION_2 = new RemoteException("2");

  @Test
  public void testSetResult() throws Exception {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    assertNotNull(task);
    assertFalse(task.isComplete());

    source.setResult(RESULT);

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertEquals(RESULT, task.getResult());
  }

  @Test
  public void testTrySetResult() throws Exception {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    assertTrue(source.trySetResult(RESULT));
    assertEquals(RESULT, task.getResult());
  }

  @Test
  public void testTrySetResult_alreadySet() throws Exception {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    source.setResult(RESULT);
    // Expect no exception here.
    assertFalse(source.trySetResult(RESULT_2));
    assertEquals(RESULT, task.getResult());
  }

  @Test
  public void testSetException() {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    assertNotNull(task);
    assertFalse(task.isComplete());

    source.setException(EXCEPTION);

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertEquals(EXCEPTION, task.getException());
  }

  @Test
  public void testTrySetException() {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    assertTrue(source.trySetException(EXCEPTION));
    assertEquals(EXCEPTION, task.getException());
  }

  @Test
  public void testTrySetException_alreadySet() {
    TaskCompletionSource<String> source = new TaskCompletionSource<>();
    Task<String> task = source.getTask();

    source.setException(EXCEPTION);
    // Expect no exception here.
    assertFalse(source.trySetException(EXCEPTION_2));
    assertEquals(EXCEPTION, task.getException());
  }
}
