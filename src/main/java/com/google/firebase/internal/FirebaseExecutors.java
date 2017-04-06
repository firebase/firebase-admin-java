package com.google.firebase.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Default executors used for internal Firebase threads. */
public class FirebaseExecutors {

  public static final ScheduledExecutorService DEFAULT_SCHEDULED_EXECUTOR;

  static {
    if (GaeThreadFactory.isAvailable()) {
      DEFAULT_SCHEDULED_EXECUTOR = GaeThreadFactory.DEFAULT_EXECUTOR;
    } else {
      DEFAULT_SCHEDULED_EXECUTOR =
          Executors.newSingleThreadScheduledExecutor(Executors.defaultThreadFactory());
    }
  }
}
