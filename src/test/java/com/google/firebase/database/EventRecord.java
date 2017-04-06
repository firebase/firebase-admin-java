package com.google.firebase.database;

import com.google.firebase.database.core.view.Event;

/**
 * User: greg Date: 5/28/13 Time: 10:01 AM
 */
public class EventRecord {

  private DataSnapshot snapshot;
  private Event.EventType eventType;
  private String previousChild;

  public EventRecord(DataSnapshot snapshot, Event.EventType eventType, String previousChild) {
    this.snapshot = snapshot;
    this.eventType = eventType;
    this.previousChild = previousChild;
  }

  public DataSnapshot getSnapshot() {
    return snapshot;
  }

  public Event.EventType getEventType() {
    return eventType;
  }

  public String getPreviousChild() {
    return previousChild;
  }

  @Override
  public String toString() {
    return "Event: " + eventType + " at " + snapshot.getRef().toString();
  }
}
