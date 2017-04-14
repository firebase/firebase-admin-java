package com.google.firebase.database.core;

import com.google.firebase.database.connection.PersistentConnection;
import java.lang.Thread.UncaughtExceptionHandler;

public class CoreTestHelpers {

  public static void freezeContext(Context context) {
    context.freeze();
  }

  public static PersistentConnection getRepoConnection(Repo repo) {
    return repo.getConnection();
  }

  public static void setEventTargetExceptionHandler(Context context,
      UncaughtExceptionHandler handler) {
    ThreadPoolEventTarget eventTarget = (ThreadPoolEventTarget) context.getEventTarget();
    eventTarget.setExceptionHandler(handler);
  }

  public static UncaughtExceptionHandler getEventTargetExceptionHandler(Context context) {
    ThreadPoolEventTarget eventTarget = (ThreadPoolEventTarget) context.getEventTarget();
    return eventTarget.getExceptionHandler();
  }
}
