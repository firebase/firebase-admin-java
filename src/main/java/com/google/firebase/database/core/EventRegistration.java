package com.google.firebase.database.core;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QuerySpec;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class EventRegistration {

  private AtomicBoolean zombied = new AtomicBoolean(false);
  private EventRegistrationZombieListener listener;
  private boolean isUserInitiated = false;

  public abstract boolean respondsTo(Event.EventType eventType);

  public abstract DataEvent createEvent(Change change, QuerySpec query);

  public abstract void fireEvent(DataEvent dataEvent);

  public abstract void fireCancelEvent(DatabaseError error);

  public abstract EventRegistration clone(QuerySpec newQuery);

  public abstract boolean isSameListener(EventRegistration other);

  @NotNull
  public abstract QuerySpec getQuerySpec();

  public void zombify() {
    if (zombied.compareAndSet(false, true)) {
      if (listener != null) {
        listener.onZombied(this);
        listener = null;
      }
    }
  }

  public boolean isZombied() {
    return zombied.get();
  }

  public void setOnZombied(EventRegistrationZombieListener listener) {
    assert !isZombied();
    assert this.listener == null;
    this.listener = listener;
  }

  public boolean isUserInitiated() {
    return isUserInitiated;
  }

  public void setIsUserInitiated(boolean isUserInitiated) {
    this.isUserInitiated = isUserInitiated;
  }

  // Used for Testing only.
  Repo getRepo() {
    return null;
  }
}
