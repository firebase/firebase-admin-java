package com.google.firebase.database.utilities;

import com.google.firebase.database.core.Context;
import java.lang.Thread.UncaughtExceptionHandler;

/**
 * A collection of helper methods for manipulating the DefaultRunLoop instance used by
 * FirebaseDatabase. Provides access to the package-private instance methods in DefaultRunLoop
 * class.
 */
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
