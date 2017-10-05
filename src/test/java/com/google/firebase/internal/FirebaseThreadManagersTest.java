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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.FirebaseThreadManagers.GlobalThreadManager;
import com.google.firebase.tasks.Tasks;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

public class FirebaseThreadManagersTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGlobalThreadManager() {
    MockThreadManager threadManager = new MockThreadManager();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setThreadManager(threadManager)
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    assertEquals(1, threadManager.initCount);

    ExecutorService exec1 = threadManager.getExecutor(defaultApp);
    assertEquals(1, threadManager.initCount);
    assertFalse(exec1.isShutdown());

    ExecutorService exec2 = threadManager.getExecutor(defaultApp);
    assertEquals(1, threadManager.initCount);
    assertFalse(exec2.isShutdown());

    // Should return the same executor for both invocations.
    assertSame(exec1, exec2);

    threadManager.releaseExecutor(defaultApp, exec1);
    assertTrue(exec1.isShutdown());
  }

  @Test
  public void testGlobalThreadManagerWithMultipleApps() {
    MockThreadManager threadManager = new MockThreadManager();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    FirebaseApp customApp = FirebaseApp.initializeApp(options, "customApp");
    assertEquals(0, threadManager.initCount);

    ExecutorService exec1 = threadManager.getExecutor(defaultApp);
    ExecutorService exec2 = threadManager.getExecutor(customApp);
    assertEquals(1, threadManager.initCount);
    assertFalse(exec1.isShutdown());

    // Should return the same executor for both invocations.
    assertSame(exec1, exec2);

    threadManager.releaseExecutor(defaultApp, exec1);
    assertFalse(exec1.isShutdown());

    threadManager.releaseExecutor(customApp, exec2);
    assertTrue(exec1.isShutdown());
  }

  @Test
  public void testGlobalThreadManagerReInit() {
    MockThreadManager threadManager = new MockThreadManager();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setThreadManager(threadManager)
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    assertEquals(1, threadManager.initCount);

    ExecutorService exec1 = threadManager.getExecutor(defaultApp);
    assertEquals(1, threadManager.initCount);
    assertFalse(exec1.isShutdown());

    // Simulate app.delete()
    threadManager.releaseExecutor(defaultApp, exec1);
    assertTrue(exec1.isShutdown());

    // Simulate app re-init
    ExecutorService exec2 = threadManager.getExecutor(defaultApp);
    assertEquals(2, threadManager.initCount);
    assertNotSame(exec1, exec2);

    threadManager.releaseExecutor(defaultApp, exec2);
    assertTrue(exec2.isShutdown());
  }

  @Test
  public void testDefaultThreadManager() throws Exception {
    Assume.assumeFalse(GaeThreadFactory.isAvailable());

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    final Map<String, Object> threadInfo = new HashMap<>();
    Callable<Void> command = new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        Thread thread = Thread.currentThread();
        threadInfo.put("name", thread.getName());
        threadInfo.put("daemon", thread.isDaemon());
        return null;
      }
    };
    Tasks.await(ImplFirebaseTrampolines.submitCallable(defaultApp, command));

    // Check for default JVM thread properties.
    assertTrue(threadInfo.get("name").toString().startsWith("firebase-default-"));
    assertTrue((Boolean) threadInfo.get("daemon"));

    defaultApp.delete();
    try {
      ImplFirebaseTrampolines.submitCallable(defaultApp, command);
      fail("No error thrown when submitting to deleted app");
    } catch (RejectedExecutionException expected) {
      // expected
    }
  }

  private static class MockThreadManager extends GlobalThreadManager {

    private int initCount = 0;

    @Override
    protected ExecutorService doInit() {
      initCount++;
      return Executors.newSingleThreadExecutor();
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
