package com.google.firebase.database.core.view;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;

public class CancelEvent implements Event {

  private final Path path;
  private final EventRegistration eventRegistration;
  private final DatabaseError error;

  public CancelEvent(EventRegistration eventRegistration, DatabaseError error, Path path) {
    this.eventRegistration = eventRegistration;
    this.path = path;
    this.error = error;
  }

  @Override
  public Path getPath() {
    return this.path;
  }

  @Override
  public void fire() {
    this.eventRegistration.fireCancelEvent(this.error);
  }

  @Override
  public String toString() {
    return this.getPath() + ":" + "CANCEL";
  }
}
