package com.google.firebase.database;

public class TestFailure extends Exception {

  public TestFailure(Throwable e) {
    super(e);
  }

  public TestFailure(String message) {
    super(message);
  }
}
