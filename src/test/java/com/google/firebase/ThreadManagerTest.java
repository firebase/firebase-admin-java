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
import static org.junit.Assert.assertSame;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ThreadManagerTest {

  private ExecutorService executor;

  @Before
  public void setUp() {
    executor = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    executor.shutdownNow();
  }

  @Test
  public void testBareBonesAppLifecycle() {
    MockThreadManager threadManager = new MockThreadManager(executor);

    // Initializing an app doesn't initialize any threading resources.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(0, threadManager.events.size());

    // Since no threading resources were initialized, nothing to clean up.
    app.delete();
    assertEquals(0, threadManager.events.size());
  }

  @Test
  public void testAppLifecycleWithExecutor() {
    MockThreadManager threadManager = new MockThreadManager(executor);

    // Initializing an app doesn't initialize any threading resources.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(0, threadManager.events.size());

    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);

    // Should not re-initialize
    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(1, threadManager.events.size());

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);
    assertSame(executor, event.arguments[1]);
  }

  @Test
  public void testAppLifecycleWithTokenRefresh() {
    MockThreadManager threadManager = new MockThreadManager(executor);

    // Initializing an app doesn't initialize any threading resources.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(0, threadManager.events.size());

    // Refreshing the token this way kicks off the proactive token refresh task, which initializes
    // an executor to schedule the next refresh task.
    TestOnlyImplFirebaseTrampolines.getToken(app, true);
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_THREAD_FACTORY, event.type);

    TestOnlyImplFirebaseTrampolines.forceThreadManagerInit(app);
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(3, threadManager.events.size());
    event = threadManager.events.get(2);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);
    assertSame(executor, event.arguments[1]);
  }

  @Test
  public void testAppLifecycleWithServiceCall() {
    MockThreadManager threadManager = new MockThreadManager(executor);

    // Initializing an app doesn't initialize any threading resources.
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));
    assertEquals(0, threadManager.events.size());

    // Initializing auth should not initialize threading resources.
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertEquals(0, threadManager.events.size());

    // Calling an async method on auth should initialize threading resources.
    try {
      auth.verifyIdTokenAsync("foo").get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);

    // Deleting app should tear down threading resources.
    app.delete();
    assertEquals(2, threadManager.events.size());
    event = threadManager.events.get(1);
    assertEquals(Event.TYPE_RELEASE_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);
    assertSame(executor, event.arguments[1]);
  }

  @Test
  public void testAppLifecycleWithMultipleServiceCalls()
      throws ExecutionException, InterruptedException {
    MockThreadManager threadManager = new MockThreadManager(executor);
    FirebaseApp app = FirebaseApp.initializeApp(buildOptions(threadManager));

    // Initializing an app doesn't initialize any threading resources.
    assertEquals(0, threadManager.events.size());

    // Initializing auth should not initialize threading resources.
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertEquals(0, threadManager.events.size());

    // Calling an async method on auth should initialize threading resources.
    try {
      auth.verifyIdTokenAsync("foo").get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    assertEquals(1, threadManager.events.size());
    Event event = threadManager.events.get(0);
    assertEquals(Event.TYPE_GET_EXECUTOR, event.type);
    assertSame(app, event.arguments[0]);

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
    assertSame(app, event.arguments[0]);
    assertSame(executor, event.arguments[1]);
  }

  private FirebaseOptions buildOptions(ThreadManager threadManager) {
    return new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials())
        .setProjectId("mock-project-id")
        .setThreadManager(threadManager)
        .build();
  }

  private static class MockThreadManager extends ThreadManager {

    private final List<Event> events = new ArrayList<>();
    private final ExecutorService executor;

    MockThreadManager(ExecutorService executor) {
      this.executor = executor;
    }

    @Override
    protected ExecutorService getExecutor(@NonNull FirebaseApp app) {
      events.add(new Event(Event.TYPE_GET_EXECUTOR, app));
      return executor;
    }

    @Override
    protected void releaseExecutor(@NonNull FirebaseApp app, @NonNull ExecutorService executor) {
      events.add(new Event(Event.TYPE_RELEASE_EXECUTOR, app, executor));
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      events.add(new Event(Event.TYPE_GET_THREAD_FACTORY));
      return Executors.defaultThreadFactory();
    }
  }
  
  private static class Event {
    private static final int TYPE_GET_EXECUTOR = 100;
    private static final int TYPE_RELEASE_EXECUTOR = 101;
    private static final int TYPE_GET_THREAD_FACTORY = 102;

    private final int type;
    private final Object[] arguments;

    public Event(int type, Object... arguments) {
      this.type = type;
      this.arguments = arguments;
    }
  }
}
