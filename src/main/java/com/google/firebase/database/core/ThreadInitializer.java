package com.google.firebase.database.core;

import java.lang.Thread.UncaughtExceptionHandler;

public interface ThreadInitializer {

  ThreadInitializer defaultInstance =
      new ThreadInitializer() {
        @Override
        public void setName(Thread t, String name) {
          t.setName(name);
        }

        @Override
        public void setDaemon(Thread t, boolean isDaemon) {
          t.setDaemon(isDaemon);
        }

        @Override
        public void setUncaughtExceptionHandler(Thread t, UncaughtExceptionHandler handler) {
          t.setUncaughtExceptionHandler(handler);
        }
      };

  void setName(Thread t, String name);

  void setDaemon(Thread t, boolean isDaemon);

  void setUncaughtExceptionHandler(Thread t, Thread.UncaughtExceptionHandler handler);
}
