package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

public class FirebaseScheduledExecutor extends ScheduledThreadPoolExecutor {

  public FirebaseScheduledExecutor(ThreadFactory threadFactory, String name) {
    this(threadFactory, name, null);
  }

  public FirebaseScheduledExecutor(
      ThreadFactory threadFactory, String name, Thread.UncaughtExceptionHandler handler) {
    super(1, decorateThreadFactory(threadFactory, name, handler));
    setRemoveOnCancelPolicy(true);
  }

  static ThreadFactory decorateThreadFactory(ThreadFactory threadFactory, String name) {
    return decorateThreadFactory(threadFactory, name, null);
  }

  private static ThreadFactory decorateThreadFactory(
      ThreadFactory threadFactory, String name, Thread.UncaughtExceptionHandler handler) {
    checkArgument(!Strings.isNullOrEmpty(name));
    ThreadFactoryBuilder builder = new ThreadFactoryBuilder()
        .setThreadFactory(threadFactory)
        .setNameFormat(name)
        .setDaemon(true);
    if (handler != null) {
      builder.setUncaughtExceptionHandler(handler);
    }
    return builder.build();
  }
}
