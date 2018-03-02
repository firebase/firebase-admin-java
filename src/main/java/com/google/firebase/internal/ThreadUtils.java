package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;

public class ThreadUtils {

  public static ThreadFactory decorateThreadFactory(ThreadFactory threadFactory, String name) {
    checkArgument(!Strings.isNullOrEmpty(name));
    // Create threads as daemons to ensure JVM exit when all foreground jobs are complete.
    return new ThreadFactoryBuilder()
        .setThreadFactory(threadFactory)
        .setNameFormat(name)
        .setDaemon(true)
        .build();
  }

  public static ThreadFactory decorateThreadFactory(ThreadFactory threadFactory, String name,
                                                    Thread.UncaughtExceptionHandler handler) {
    checkArgument(!Strings.isNullOrEmpty(name));
    // Create threads as daemons to ensure JVM exit when all foreground jobs are complete.
    return new ThreadFactoryBuilder()
        .setThreadFactory(threadFactory)
        .setNameFormat(name)
        .setDaemon(true)
        .setUncaughtExceptionHandler(handler)
        .build();
  }

}
