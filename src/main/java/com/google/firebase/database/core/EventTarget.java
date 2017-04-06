package com.google.firebase.database.core;

/**
 * This interface defines the operations required for the Firebase Database library to fire
 * callbacks. Most users should not need this interface, it is only applicable if you are
 * customizing the way in which callbacks are triggered.
 */
public interface EventTarget {

  /**
   * This method will be called from the library's event loop whenever there is a new callback to
   * be triggered.
   *
   * @param r The callback to be run
   */
  void postEvent(Runnable r);

  void shutdown();

  void restart();
}
