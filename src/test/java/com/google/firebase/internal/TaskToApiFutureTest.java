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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.core.ApiFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class TaskToApiFutureTest {

  @Test
  public void testGetResult() throws Exception {
    Task<String> task = Tasks.forResult("test");
    ApiFuture<String> future = new TaskToApiFuture<>(task);
    assertEquals("test", future.get());
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }

  @Test
  public void testGetError() throws Exception {
    Task<String> task = Tasks.forException(new RuntimeException("test"));
    ApiFuture<String> future = new TaskToApiFuture<>(task);
    try {
      future.get();
    } catch (ExecutionException e) {
      assertEquals("test", e.getCause().getMessage());
    }
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }

  @Test
  public void testAddListener() throws Exception {
    Task<String> task = Tasks.forResult("test");
    ApiFuture<String> future = new TaskToApiFuture<>(task);
    final AtomicBoolean result = new AtomicBoolean(false);
    future.addListener(new Runnable() {
      @Override
      public void run() {
        result.set(true);
      }
    }, MoreExecutors.directExecutor());
    assertEquals("test", future.get());
    assertTrue(result.get());
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }

  @Test
  public void testCancel() throws Exception {
    Task<String> task = Tasks.forResult("test");
    ApiFuture<String> future = new TaskToApiFuture<>(task);
    assertFalse(future.cancel(true));
    assertEquals("test", future.get());
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
  }
}
