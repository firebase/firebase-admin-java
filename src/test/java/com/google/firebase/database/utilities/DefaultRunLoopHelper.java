package com.google.firebase.database.utilities;

import com.google.firebase.database.core.Context;
import java.lang.Thread.UncaughtExceptionHandler;

public class DefaultRunLoopHelper {

  public static void setRunLoopExceptionHandler(Context context,
      UncaughtExceptionHandler handler) {
    DefaultRunLoop runLoop = (DefaultRunLoop) context.getRunLoop();
    runLoop.setExceptionHandler(handler);
  }

  public static UncaughtExceptionHandler getRunLoopExceptionHandler(Context context) {
    DefaultRunLoop runLoop = (DefaultRunLoop) context.getRunLoop();
    return runLoop.getExceptionHandler();
  }

}
