package com.google.firebase.database.utilities;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.RunLoop;
import com.google.firebase.internal.RevivingScheduledExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class DefaultRunLoop implements RunLoop {

  public abstract void handleException(Throwable e);

  private ScheduledThreadPoolExecutor executor;

  /**
   * Creates a DefaultRunLoop that does not periodically restart its threads.
   */
  public DefaultRunLoop() {
    this(Executors.defaultThreadFactory(), false, null);
  }

  /**
   * Creates a DefaultRunLoop that optionally restarts its threads periodically. If 'context' is
   * provided, these restarts will automatically interrupt and resume all Repo connections.
   */
  public DefaultRunLoop(
      final ThreadFactory threadFactory,
      final boolean periodicRestart,
      @Nullable final Context context) {
    executor =
        new RevivingScheduledExecutor(threadFactory, "FirebaseDatabaseWorker", periodicRestart) {
          @Override
          protected void handleException(Throwable t) {
            DefaultRunLoop.this.handleException(t);
          }

          @Override
          protected void beforeRestart() {
            if (context != null) {
              RepoManager.interrupt(context);
            }
          }

          @Override
          protected void afterRestart() {
            if (context != null) {
              RepoManager.resume(context);
            }
          }
        };

    // Core threads don't time out, this only takes effect when we drop the number of required
    // core threads
    executor.setKeepAliveTime(3, TimeUnit.SECONDS);
  }

  public ScheduledExecutorService getExecutorService() {
    return this.executor;
  }

  @Override
  public void scheduleNow(final Runnable runnable) {
    executor.execute(runnable);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public ScheduledFuture schedule(final Runnable runnable, long milliseconds) {
    return executor.schedule(runnable, milliseconds, TimeUnit.MILLISECONDS);
  }

  @Override
  public void shutdown() {
    executor.setCorePoolSize(0);
  }

  @Override
  public void restart() {
    executor.setCorePoolSize(1);
  }

  public static String messageForException(Throwable t) {
    if (t instanceof OutOfMemoryError) {
      return "Firebase Database encountered an OutOfMemoryError. You may need to reduce the"
          + " amount of data you are syncing to the client (e.g. by using queries or syncing"
          + " a deeper path). See "
          + "https://firebase.google.com/docs/database/ios/structure-data#best_practices_for_data_structure"
          + " and "
          + "https://firebase.google.com/docs/database/android/retrieve-data#filtering_data";
    } else if (t instanceof DatabaseException) {
      // Exception should be self-explanatory and they shouldn't contact support.
      return "";
    } else {
      return "Uncaught exception in Firebase Database runloop ("
          + FirebaseDatabase.getSdkVersion()
          + "). Please report to firebase-database-client@google.com";
    }
  }
}
