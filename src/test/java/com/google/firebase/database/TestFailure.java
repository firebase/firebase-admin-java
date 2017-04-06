package com.google.firebase.database;

/** User: greg Date: 5/30/13 Time: 9:46 AM */
public class TestFailure extends Exception {

  public TestFailure(Throwable e) {
    super(e);
  }

  public TestFailure(String message) {
    super(message);
  }
}
