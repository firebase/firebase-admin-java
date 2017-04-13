package com.google.firebase.database.utilities;

public class DefaultClock implements Clock {

  @Override
  public long millis() {
    return System.currentTimeMillis();
  }
}
