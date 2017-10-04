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

package com.google.firebase;

import static org.junit.Assert.assertEquals;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.junit.After;
import org.junit.Test;

public class ThreadManagerTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testBareBonesAppLifecycle() {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Deleting the app should clean up the executor.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app, event.app);
  }

  @Test
  public void testBareBonesAppLifecycleWithMultipleApps() {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    final FirebaseApp app1 = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app1, event.app);

    final FirebaseApp app2 = FirebaseApp.initializeApp(buildOptions(threadManager), "otherApp");
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app2, event.app);

    // Deleting the app should clean up the executor.
    app1.delete();
    assertEquals(3, threadManager.events.size());
    event = threadManager.events.get(2);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app1, event.app);

    app2.delete();
    assertEquals(4, threadManager.events.size());
    event = threadManager.events.get(3);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app2, event.app);
  }

  @Test
  public void testAppLifecycleWithExecutor() {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Should not re-initialize
    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(1, threadManager.events.size());

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app, event.app);
  }

  @Test
  public void testAppLifecycleWithTokenRefresh() {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Refreshing the token this way kicks off the proactive token refresh task, which initializes
    // the executor to schedule the next refresh task.
    TestOnlyImplFirebaseTrampolines.getToken(app, true);
    assertEquals(1, threadManager.events.size());

    // Should not re-initialize
    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(1, threadManager.events.size());

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app, event.app);
  }

  @Test
  public void testAppLifecycleWithServiceCall() {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Initializing auth should not initialize any more threading resources.
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertEquals(1, threadManager.events.size());

    // Calling an async method on auth should not re-initialize threading resources.
    try {
      auth.verifyIdTokenAsync("foo").get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    assertEquals(1, threadManager.events.size());

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app, event.app);
  }

  @Test
  public void testAppLifecycleWithMultipleServiceCalls()
      throws ExecutionException, InterruptedException {
    MockThreadManager threadManager = new MockThreadManager();

    // Initializing an app should initialize the executor.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Initializing auth should not re-initialize threading resources.
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertEquals(1, threadManager.events.size());

    // Calling an async method on auth should not re-initialize threading resources.
    try {
      auth.verifyIdTokenAsync("foo").get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    assertEquals(1, threadManager.events.size());
    event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertEquals(app, event.app);

    // Calling a method again should not re-initialize threading resources.
    try {
      auth.verifyIdTokenAsync("foo").get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    assertEquals(1, threadManager.events.size());

    // Should not re-initialize threading resources.
    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(1, threadManager.events.size());

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertEquals(app, event.app);
  }

  private FirebaseOptions buildOptions(ThreadManager threadManager) {
    return new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setProjectId("mock-project-id")
        .setThreadManager(threadManager)
        .build();
  }

  public static class MockThreadManager extends ThreadManager {

    private final List<Event> events = new ArrayList<>();

    @Override
    protected ScheduledExecutorService getExecutor(@NonNull FirebaseApp app) {
      events.add(new Event(Event.TYPE_GET_EXECUTOR, app, null));
      return Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    protected void releaseExecutor(@NonNull FirebaseApp app,
        @NonNull ScheduledExecutorService executor) {
      events.add(new Event(Event.TYPE_RELEASE_EXECUTOR, app, executor));
      executor.shutdownNow();
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      events.add(new Event(Event.TYPE_GET_THREAD_FACTORY, null, null));
      return Executors.defaultThreadFactory();
    }
  }
  
  private static class Event {
    private static final int TYPE_GET_EXECUTOR = 100;
    private static final int TYPE_RELEASE_EXECUTOR = 101;
    private static final int TYPE_GET_THREAD_FACTORY = 102;

    private final int type;
    private final FirebaseApp app;
    private ScheduledExecutorService executor;

    public Event(int type, @Nullable FirebaseApp app, @Nullable ScheduledExecutorService executor) {
      this.type = type;
      this.app = app;
      this.executor = executor;
    }
  }
}
