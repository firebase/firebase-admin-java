package com.google.firebase.database.core.view.filter;

import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildChangeAccumulator {

  private final Map<ChildKey, Change> changeMap;

  public ChildChangeAccumulator() {
    this.changeMap = new HashMap<>();
  }

  public void trackChildChange(Change change) {
    Event.EventType type = change.getEventType();
    ChildKey childKey = change.getChildKey();
    assert type == Event.EventType.CHILD_ADDED
        || type == Event.EventType.CHILD_CHANGED
        || type == Event.EventType.CHILD_REMOVED
        : "Only child changes supported for tracking";
    assert !change.getChildKey().isPriorityChildName();
    if (changeMap.containsKey(childKey)) {
      Change oldChange = changeMap.get(childKey);
      Event.EventType oldType = oldChange.getEventType();
      if (type == Event.EventType.CHILD_ADDED && oldType == Event.EventType.CHILD_REMOVED) {
        changeMap.put(
            change.getChildKey(),
            Change.childChangedChange(
                childKey, change.getIndexedNode(), oldChange.getIndexedNode()));
      } else if (type == Event.EventType.CHILD_REMOVED && oldType == Event.EventType.CHILD_ADDED) {
        changeMap.remove(childKey);
      } else if (type == Event.EventType.CHILD_REMOVED
          && oldType == Event.EventType.CHILD_CHANGED) {
        changeMap.put(childKey, Change.childRemovedChange(childKey, oldChange.getOldIndexedNode()));
      } else if (type == Event.EventType.CHILD_CHANGED && oldType == Event.EventType.CHILD_ADDED) {
        changeMap.put(childKey, Change.childAddedChange(childKey, change.getIndexedNode()));
      } else if (type == Event.EventType.CHILD_CHANGED
          && oldType == Event.EventType.CHILD_CHANGED) {
        changeMap.put(
            childKey,
            Change.childChangedChange(
                childKey, change.getIndexedNode(), oldChange.getOldIndexedNode()));
      } else {
        throw new IllegalStateException(
            "Illegal combination of changes: " + change + " occurred after " + oldChange);
      }
    } else {
      changeMap.put(change.getChildKey(), change);
    }
  }

  public List<Change> getChanges() {
    return new ArrayList<>(this.changeMap.values());
  }
}
