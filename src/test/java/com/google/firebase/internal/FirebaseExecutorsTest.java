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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.FirebaseExecutors.GlobalThreadManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.junit.After;
import org.junit.Test;

public class FirebaseExecutorsTest {

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
    assertEquals(0, threadManager.initCount);

    ScheduledExecutorService exec1 = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    assertEquals(1, threadManager.initCount);

    ScheduledExecutorService exec2 = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    assertEquals(1, threadManager.initCount);
    assertSame(exec1, exec2);

    defaultApp.delete();
    assertTrue(exec1.isShutdown());
  }

  @Test
  public void testGlobalThreadManagerWithMultipleApps() {
    MockThreadManager threadManager = new MockThreadManager();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setThreadManager(threadManager)
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    FirebaseApp customApp = FirebaseApp.initializeApp(options, "customApp");
    assertEquals(0, threadManager.initCount);

    TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    TestOnlyImplFirebaseTrampolines.getExecutorService(customApp);
    assertEquals(1, threadManager.initCount);

    ScheduledExecutorService exec1 = threadManager.getExecutor(defaultApp);
    ScheduledExecutorService exec2 = threadManager.getExecutor(customApp);
    assertEquals(1, threadManager.initCount);
    assertSame(exec1, exec2);

    defaultApp.delete();
    assertFalse(exec1.isShutdown());

    customApp.delete();
    assertTrue(exec1.isShutdown());
  }

  @Test
  public void testGlobalThreadManagerReInitApp() {
    MockThreadManager threadManager = new MockThreadManager();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setThreadManager(threadManager)
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    assertEquals(0, threadManager.initCount);

    ScheduledExecutorService exec1 = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    assertEquals(1, threadManager.initCount);

    ScheduledExecutorService exec2 = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    assertEquals(1, threadManager.initCount);
    assertSame(exec1, exec2);

    defaultApp.delete();
    assertTrue(exec1.isShutdown());

    defaultApp = FirebaseApp.initializeApp(options);
    assertEquals(1, threadManager.initCount);
    ScheduledExecutorService exec3 = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    assertEquals(2, threadManager.initCount);

    defaultApp.delete();
    assertTrue(exec3.isShutdown());
  }

  @Test
  public void testDefaultThreadManager() throws Exception {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    FirebaseApp defaultApp = FirebaseApp.initializeApp(options);
    ScheduledExecutorService exec = TestOnlyImplFirebaseTrampolines.getExecutorService(defaultApp);
    final Map<String, Object> threadInfo = new HashMap<>();
    exec.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        Thread thread = Thread.currentThread();
        threadInfo.put("name", thread.getName());
        threadInfo.put("daemon", thread.isDaemon());
        return null;
      }
    }).get();

    assertTrue(threadInfo.get("name").toString().startsWith("firebase-default-"));
    assertTrue((Boolean) threadInfo.get("daemon"));

    defaultApp.delete();
    assertTrue(exec.isShutdown());
  }

  private static class MockThreadManager extends GlobalThreadManager {

    private int initCount = 0;

    @Override
    protected ScheduledExecutorService doInit() {
      initCount++;
      return Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    protected void doCleanup(ScheduledExecutorService executorService) {
      executorService.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      return Executors.defaultThreadFactory();
    }
  }
}
