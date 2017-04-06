package com.google.firebase.database.core.persistence;

import com.google.firebase.database.core.view.QuerySpec;

public class TrackedQuery {

  public final long id;
  public final QuerySpec querySpec;
  public final long lastUse;
  public final boolean complete;
  public final boolean active;

  public TrackedQuery(
      long id, QuerySpec querySpec, long lastUse, boolean complete, boolean active) {
    this.id = id;
    if (querySpec.loadsAllData() && !querySpec.isDefault()) {
      throw new IllegalArgumentException(
          "Can't create TrackedQuery for a non-default query that loads all data");
    }
    this.querySpec = querySpec;
    this.lastUse = lastUse;
    this.complete = complete;
    this.active = active;
  }

  public TrackedQuery updateLastUse(long lastUse) {
    return new TrackedQuery(this.id, this.querySpec, lastUse, this.complete, this.active);
  }

  public TrackedQuery setComplete() {
    return new TrackedQuery(this.id, this.querySpec, this.lastUse, true, this.active);
  }

  public TrackedQuery setActiveState(boolean isActive) {
    return new TrackedQuery(this.id, this.querySpec, this.lastUse, this.complete, isActive);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o == null || o.getClass() != this.getClass()) {
      return false;
    }

    TrackedQuery query = (TrackedQuery) o;
    return this.id == query.id
        && this.querySpec.equals(query.querySpec)
        && this.lastUse == query.lastUse
        && this.complete == query.complete
        && this.active == query.active;
  }

  @Override
  public int hashCode() {
    int result = Long.valueOf(this.id).hashCode();
    result = 31 * result + this.querySpec.hashCode();
    result = 31 * result + Long.valueOf(this.lastUse).hashCode();
    result = 31 * result + Boolean.valueOf(this.complete).hashCode();
    result = 31 * result + Boolean.valueOf(this.active).hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "TrackedQuery{"
        + "id="
        + id
        + ", querySpec="
        + querySpec
        + ", lastUse="
        + lastUse
        + ", complete="
        + complete
        + ", active="
        + active
        + "}";
  }
}
