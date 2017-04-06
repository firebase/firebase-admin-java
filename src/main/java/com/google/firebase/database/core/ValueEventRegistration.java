package com.google.firebase.database.core;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QuerySpec;

public class ValueEventRegistration extends EventRegistration {

  private final Repo repo;
  private final ValueEventListener eventListener;
  private final QuerySpec spec;

  public ValueEventRegistration(
      Repo repo, ValueEventListener eventListener, @NotNull QuerySpec spec) {
    this.repo = repo;
    this.eventListener = eventListener;
    this.spec = spec;
  }

  @Override
  public boolean respondsTo(Event.EventType eventType) {
    return eventType == Event.EventType.VALUE;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ValueEventRegistration
        && ((ValueEventRegistration) other).eventListener.equals(eventListener)
        && ((ValueEventRegistration) other).repo.equals(repo)
        && ((ValueEventRegistration) other).spec.equals(spec);
  }

  @Override
  public int hashCode() {
    int result = this.eventListener.hashCode();
    result = 31 * result + this.repo.hashCode();
    result = 31 * result + this.spec.hashCode();
    return result;
  }

  @Override
  public DataEvent createEvent(Change change, QuerySpec query) {
    DatabaseReference ref = InternalHelpers.createReference(repo, query.getPath());

    DataSnapshot dataSnapshot = InternalHelpers.createDataSnapshot(ref, change.getIndexedNode());
    return new DataEvent(Event.EventType.VALUE, this, dataSnapshot, null);
  }

  @Override
  public void fireEvent(final DataEvent eventData) {
    if (isZombied()) {
      return;
    }
    eventListener.onDataChange(eventData.getSnapshot());
  }

  @Override
  public void fireCancelEvent(final DatabaseError error) {
    eventListener.onCancelled(error);
  }

  @Override
  public EventRegistration clone(QuerySpec newQuery) {
    return new ValueEventRegistration(this.repo, this.eventListener, newQuery);
  }

  @Override
  public boolean isSameListener(EventRegistration other) {
    return (other instanceof ValueEventRegistration)
        && ((ValueEventRegistration) other).eventListener.equals(eventListener);
  }

  @NotNull
  @Override
  public QuerySpec getQuerySpec() {
    return spec;
  }

  @Override
  public String toString() {
    return "ValueEventRegistration";
  }

  // Package private for testing purposes only
  @Override
  Repo getRepo() {
    return repo;
  }
}
