package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;

public abstract class FirebaseService<T> {

  private final String id;
  protected final T instance;

  protected FirebaseService(String id, T instance) {
    checkArgument(!Strings.isNullOrEmpty(id));
    this.id = id;
    this.instance = checkNotNull(instance);
  }

  public final String getId() {
    return id;
  }

  public final T getInstance() {
    return instance;
  }

  public abstract void destroy();
}
