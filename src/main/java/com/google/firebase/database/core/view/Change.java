package com.google.firebase.database.core.view;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;

public class Change {

  private final Event.EventType eventType;
  private final IndexedNode indexedNode;
  private final IndexedNode oldIndexedNode;
  private final ChildKey childKey;
  private final ChildKey prevName;

  private Change(
      Event.EventType eventType,
      IndexedNode indexedNode,
      ChildKey childKey,
      ChildKey prevName,
      IndexedNode oldIndexedNode) {
    this.eventType = eventType;
    this.indexedNode = indexedNode;
    this.childKey = childKey;
    this.prevName = prevName;
    this.oldIndexedNode = oldIndexedNode;
  }

  public static Change valueChange(IndexedNode snapshot) {
    return new Change(Event.EventType.VALUE, snapshot, null, null, null);
  }

  public static Change childAddedChange(ChildKey childKey, Node snapshot) {
    return childAddedChange(childKey, IndexedNode.from(snapshot));
  }

  public static Change childAddedChange(ChildKey childKey, IndexedNode snapshot) {
    return new Change(Event.EventType.CHILD_ADDED, snapshot, childKey, null, null);
  }

  public static Change childRemovedChange(ChildKey childKey, Node snapshot) {
    return childRemovedChange(childKey, IndexedNode.from(snapshot));
  }

  public static Change childRemovedChange(ChildKey childKey, IndexedNode snapshot) {
    return new Change(Event.EventType.CHILD_REMOVED, snapshot, childKey, null, null);
  }

  public static Change childChangedChange(ChildKey childKey, Node newSnapshot, Node oldSnapshot) {
    return childChangedChange(
        childKey, IndexedNode.from(newSnapshot), IndexedNode.from(oldSnapshot));
  }

  public static Change childChangedChange(
      ChildKey childKey, IndexedNode newSnapshot, IndexedNode oldSnapshot) {
    return new Change(Event.EventType.CHILD_CHANGED, newSnapshot, childKey, null, oldSnapshot);
  }

  public static Change childMovedChange(ChildKey childKey, Node snapshot) {
    return Change.childMovedChange(childKey, IndexedNode.from(snapshot));
  }

  public static Change childMovedChange(ChildKey childKey, IndexedNode snapshot) {
    return new Change(Event.EventType.CHILD_MOVED, snapshot, childKey, null, null);
  }

  public Change changeWithPrevName(ChildKey prevName) {
    return new Change(eventType, indexedNode, childKey, prevName, oldIndexedNode);
  }

  public ChildKey getChildKey() {
    return childKey;
  }

  public Event.EventType getEventType() {
    return eventType;
  }

  public IndexedNode getIndexedNode() {
    return indexedNode;
  }

  public ChildKey getPrevName() {
    return prevName;
  }

  public IndexedNode getOldIndexedNode() {
    return this.oldIndexedNode;
  }

  @Override
  public String toString() {
    return "Change: " + eventType + " " + childKey;
  }
}
