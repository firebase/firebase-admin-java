package com.google.firebase.database.core.view;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;

public class DataEvent implements Event {

  private final EventType eventType;
  private final EventRegistration eventRegistration;
  private final DataSnapshot snapshot;
  private final String prevName;

  public DataEvent(
      EventType eventType,
      EventRegistration eventRegistration,
      DataSnapshot snapshot,
      String prevName) {
    this.eventType = eventType;
    this.eventRegistration = eventRegistration;
    this.snapshot = snapshot;
    this.prevName = prevName;
  }

  @Override
  public Path getPath() {
    Path path = this.snapshot.getRef().getPath();
    if (this.eventType == EventType.VALUE) {
      return path;
    } else {
      return path.getParent();
    }
  }

  public DataSnapshot getSnapshot() {
    return this.snapshot;
  }

  public String getPreviousName() {
    return this.prevName;
  }

  public EventType getEventType() {
    return this.eventType;
  }

  @Override
  public void fire() {
    this.eventRegistration.fireEvent(this);
  }

  @Override
  public String toString() {
    if (this.eventType == EventType.VALUE) {
      return this.getPath() + ": " + this.eventType + ": " + this.snapshot.getValue(true);
    } else {
      return this.getPath()
          + ": "
          + this.eventType
          + ": { "
          + this.snapshot.getKey()
          + ": "
          + this.snapshot.getValue(true)
          + " }";
    }
  }
}
