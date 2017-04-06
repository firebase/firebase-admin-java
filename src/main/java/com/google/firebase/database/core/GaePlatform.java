package com.google.firebase.database.core;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.PersistentConnectionImpl;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.tubesock.WebSocket;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.Preconditions;
import com.google.firebase.internal.RevivingScheduledExecutor;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Represents a Google AppEngine platform.
 *
 * <p>This class is not thread-safe.
 */
class GaePlatform implements Platform {

  private static final String TAG = "GaePlatform";
  private static final String PROCESS_PLATFORM = "AppEngine";

  ThreadFactory threadFactoryInstance;

  private final FirebaseApp firebaseApp;

  public GaePlatform(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
  }

  @Override
  public Logger newLogger(Context ctx, Logger.Level level, List<String> components) {
    return new DefaultLogger(level, components);
  }

  private ThreadFactory getGaeThreadFactory() {
    GaeThreadFactory threadFactory = GaeThreadFactory.getInstance();
    Preconditions.checkState(threadFactory.isUsingBackgroundThreads(),
        "Failed to initialize a GAE background thread factory");
    return threadFactory;
  }

  public static boolean isActive() {
    return GaeThreadFactory.isAvailable();
  }

  public void initialize() {
    WebSocket.setThreadFactory(
        getGaeThreadFactory(),
        new com.google.firebase.database.tubesock.ThreadInitializer() {
          @Override
          public void setName(Thread thread, String s) {
            // Unsupported by GAE
          }
        });
  }

  @Override
  public EventTarget newEventTarget(Context ctx) {
    RevivingScheduledExecutor eventExecutor =
        new RevivingScheduledExecutor(getGaeThreadFactory(), "FirebaseDatabaseEventTarget", true);
    return new ThreadPoolEventTarget(eventExecutor);
  }

  @Override
  public RunLoop newRunLoop(final Context context) {
    final LogWrapper logger = context.getLogger("RunLoop");
    return new DefaultRunLoop(getGaeThreadFactory(), /* periodicRestart= */ true, context) {
      @Override
      public void handleException(Throwable e) {
        logger.error(DefaultRunLoop.messageForException(e), e);
      }
    };
  }

  @Override
  public AuthTokenProvider newAuthTokenProvider(ScheduledExecutorService executorService) {
    return new JvmAuthTokenProvider(this.firebaseApp, executorService);
  }

  @Override
  public PersistentConnection newPersistentConnection(
      Context context,
      ConnectionContext connectionContext,
      HostInfo info,
      PersistentConnection.Delegate delegate) {
    return new PersistentConnectionImpl(context.getConnectionContext(), info, delegate);
  }

  @Override
  public String getUserAgent(Context ctx) {
    return PROCESS_PLATFORM + "/" + DEVICE;
  }

  @Override
  public String getPlatformVersion() {
    return "gae-" + FirebaseDatabase.getSdkVersion();
  }

  @Override
  public PersistenceManager createPersistenceManager(Context ctx, String namespace) {
    return null;
  }
}
