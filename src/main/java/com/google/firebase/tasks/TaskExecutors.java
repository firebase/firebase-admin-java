package com.google.firebase.tasks;

import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Standard {@link Executor} instances for use with {@link Task}.
 */
public class TaskExecutors {

  /**
   * An Executor that uses a shared cached thread pool.
   */
  public static final Executor DEFAULT_THREAD_POOL;

  static {
    if (GaeThreadFactory.isAvailable()) {
      DEFAULT_THREAD_POOL = GaeThreadFactory.DEFAULT_EXECUTOR;
    } else {
      DEFAULT_THREAD_POOL = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }
  }

  /**
   * An Executor that uses the calling thread.
   */
  static final Executor DIRECT =
      new Executor() {
        @Override
        public void execute(@NonNull Runnable command) {
          command.run();
        }
      };

  private TaskExecutors() {
  }
}
