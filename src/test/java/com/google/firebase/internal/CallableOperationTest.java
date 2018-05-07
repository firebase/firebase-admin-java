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
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.FirebaseThreadManagers.GlobalThreadManager;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.junit.After;
import org.junit.Test;

public class CallableOperationTest {

  private static final String TEST_FIREBASE_THREAD = "test-firebase-thread";
  private static final FirebaseOptions OPTIONS = new Builder()
      .setCredentials(new MockGoogleCredentials())
      .setThreadManager(new MockThreadManager())
      .build();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testCallResult() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(OPTIONS);
    CallableOperation<Boolean,Exception> operation = new CallableOperation<Boolean, Exception>() {
      @Override
      protected Boolean execute() throws Exception {
        String threadName = Thread.currentThread().getName();
        return TEST_FIREBASE_THREAD.equals(threadName);
      }
    };
    assertFalse(operation.call());
    assertTrue(operation.callAsync(app).get());
  }

  @Test
  public void testCallException() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(OPTIONS);
    CallableOperation<Boolean,Exception> operation = new CallableOperation<Boolean, Exception>() {
      @Override
      protected Boolean execute() throws Exception {
        String threadName = Thread.currentThread().getName();
        if (TEST_FIREBASE_THREAD.equals(threadName)) {
          throw new Exception(threadName);
        }
        return false;
      }
    };

    assertFalse(operation.call());
    try {
      operation.callAsync(app).get();
      fail("No exception thrown");
    } catch (ExecutionException e) {
      assertEquals(TEST_FIREBASE_THREAD, e.getCause().getMessage());
    }
  }

  private static class MockThreadManager extends GlobalThreadManager {
    @Override
    protected ExecutorService doInit() {
      return Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
          .setNameFormat(TEST_FIREBASE_THREAD)
          .build());
    }

    @Override
    protected void doCleanup(ExecutorService executorService) {
      executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      return Executors.defaultThreadFactory();
    }
  }
}
