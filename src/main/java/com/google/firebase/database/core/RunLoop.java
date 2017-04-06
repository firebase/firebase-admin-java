package com.google.firebase.database.core;

import java.util.concurrent.ScheduledFuture;

/**
 * This interface defines the required functionality for the Firebase Database library's run loop.
 * Most users will not need this interface. However, if you are customizing how the Firebase
 * Database
 */
@SuppressWarnings("rawtypes")
public interface RunLoop {

  /**
   * Append this operation to the queue
   *
   * @param r The operation to run
   */
  void scheduleNow(Runnable r);

  /**
   * Schedule this operation to run after the specified delay
   *
   * @param r The operation to run
   * @param milliseconds The delay, in milliseconds
   * @return A Future that can be used to cancel the operation if it has not yet started executing
   */
  ScheduledFuture schedule(Runnable r, long milliseconds);

  void shutdown();

  void restart();
}
