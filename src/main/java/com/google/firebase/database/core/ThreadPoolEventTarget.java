package com.google.firebase.database.core;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** ThreadPoolEventTarget is an event target using a configurable threadpool. */
class ThreadPoolEventTarget implements EventTarget {

  private final ThreadPoolExecutor executor;

  public ThreadPoolEventTarget(
      final ThreadFactory wrappedFactory, final ThreadInitializer threadInitializer) {
    int poolSize = 1;
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    executor = new ThreadPoolExecutor(poolSize, poolSize, 3, TimeUnit.SECONDS, queue,
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            Thread thread = wrappedFactory.newThread(r);
            threadInitializer.setName(thread, "FirebaseDatabaseEventTarget");
            threadInitializer.setDaemon(thread, true);
            // TODO: should we set an uncaught exception handler here? Probably want
            // to let
            // exceptions happen...
            return thread;
          }
        });
  }

  public ThreadPoolEventTarget(final ThreadPoolExecutor executor) {
    this.executor = checkNotNull(executor);
  }

  @Override
  public void postEvent(Runnable r) {
    executor.execute(r);
  }

  /**
   * Our implementation of shutdown is not immediate, it merely lowers the required number of
   * threads to 0. Depending on what we set as our timeout on the executor, this will reap the event
   * target thread after some amount of time if there's no activity
   */
  @Override
  public void shutdown() {
    executor.setCorePoolSize(0);
  }

  /**
   * Rather than launching anything, this method will ensure that our executor has at least one
   * thread available. This will keep the process alive and launch the thread if it has been reaped.
   * If the thread already exists, this is a no-op
   */
  @Override
  public void restart() {
    executor.setCorePoolSize(1);
  }
}
