package com.google.firebase.database.core.utilities;

import com.google.firebase.database.utilities.Clock;

public class TestClock implements Clock {

  private long now = 1;

  @Override
  public long millis() {
    return now;
  }

  public long tick() {
    now++;
    return now;
  }
}
