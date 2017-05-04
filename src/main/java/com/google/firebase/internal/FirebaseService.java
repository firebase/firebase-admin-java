/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;

/**
 * Represents a service exposed from the Admin SDK (e.g. auth, database). Each instance of this
 * class is associated with exactly one instance of FirebaseApp. Also provides a lifecycle hook
 * to gracefully tear down the service.
 *
 * @param <T> Type of the service
 */
public abstract class FirebaseService<T> {

  private final String id;
  protected final T instance;

  protected FirebaseService(String id, T instance) {
    checkArgument(!Strings.isNullOrEmpty(id));
    this.id = id;
    this.instance = checkNotNull(instance);
  }

  /**
   * Returns the ID used to identify this FirebaseService. Implementations must return a string
   * unique to this service (e.g. full qualified class name of the service type).
   *
   * @return an ID string unique to this service type
   */
  public final String getId() {
    return id;
  }

  /**
   * Returns the concrete object instance that provides a specific Firebase service.
   *
   * @return the service object wrapped in this FirebaseService instance
   */
  public final T getInstance() {
    return instance;
  }

  /**
   * Tear down this FirebaseService instance and the service object wrapped in it, cleaning up
   * any allocated resources in the process.
   */
  public abstract void destroy();
}
