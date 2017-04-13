package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Path;

public interface Event {

  Path getPath();

  void fire();

  @Override
  String toString();

  enum EventType {
    // The order is important here and reflects the order events should be raised in
    CHILD_REMOVED,
    CHILD_ADDED,
    CHILD_MOVED,
    CHILD_CHANGED,
    VALUE
  }
}
