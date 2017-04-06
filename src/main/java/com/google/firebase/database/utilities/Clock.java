package com.google.firebase.database.utilities;

// Abstract clock that can be replaced in unit tests.
public interface Clock {

  long millis();
}
