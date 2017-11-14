package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.ThreadManager;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.NonNull;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.mockito.Mockito;

public class GaePlatformTest {

  @Test
  public void testIsActive() {
    assertEquals(GaeThreadFactory.isAvailable(), GaePlatform.isActive());
  }

  @Test
  public void testGaePlatform() {
    final AtomicInteger count = new AtomicInteger(0);
    final ThreadManager threadManager = new ThreadManager() {
      @Override
      protected ExecutorService getExecutor(@NonNull FirebaseApp app) {
        return Executors.newSingleThreadExecutor();
      }

      @Override
      protected void releaseExecutor(@NonNull FirebaseApp app,
                                     @NonNull ExecutorService executor) {
      }

      @Override
      protected ThreadFactory getThreadFactory() {
        count.incrementAndGet();
        return Executors.defaultThreadFactory();
      }
    };

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .setThreadManager(threadManager)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "gaeApp");
    try {
      GaePlatform platform = new GaePlatform(app);
      Context ctx = Mockito.mock(Context.class);
      assertNotNull(platform.newLogger(ctx, Logger.Level.DEBUG, ImmutableList.<String>of()));

      assertNotNull(platform.newEventTarget(ctx));
      assertEquals(1, count.get());

      assertNotNull(platform.newRunLoop(ctx));
      assertEquals(2, count.get());

      AuthTokenProvider authTokenProvider = platform.newAuthTokenProvider(
          Mockito.mock(ScheduledExecutorService.class));
      assertTrue(authTokenProvider instanceof JvmAuthTokenProvider);

      assertEquals("AppEngine/AdminJava", platform.getUserAgent(ctx));
      assertEquals("gae-" + FirebaseDatabase.getSdkVersion(), platform.getPlatformVersion());
      assertNull(platform.createPersistenceManager(ctx, "test"));

      ThreadInitializer threadInitializer = platform.getThreadInitializer();
      Thread t = new Thread();
      threadInitializer.setName(t, "test-name");
      threadInitializer.setDaemon(t, true);
      threadInitializer.setUncaughtExceptionHandler(t, Mockito.mock(
          Thread.UncaughtExceptionHandler.class));
      assertNotEquals("test-name", t.getName());
      assertFalse(t.isDaemon());
      assertNotNull(t.getUncaughtExceptionHandler());

    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }
}
