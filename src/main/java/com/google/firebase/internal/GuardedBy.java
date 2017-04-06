package com.google.firebase.internal;

/**
 * Indicates that the given field can only be accessed when holding a particular lock.
 */
// TODO(depoll): Remove this if we can find a safe alternative or take the dependency.
public @interface GuardedBy {

  /**
   * Name of the variable guarded by this annotation.
   */
  String value();
}

