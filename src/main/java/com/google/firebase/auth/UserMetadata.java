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

package com.google.firebase.auth;

/**
 * Contains additional metadata associated with a user account.
 */
public class UserMetadata {

  private final long creationTimestamp;
  private final long lastSignInTimestamp;

  UserMetadata(long creationTimestamp, long lastSignInTimestamp) {
    this.creationTimestamp = creationTimestamp;
    this.lastSignInTimestamp = lastSignInTimestamp;
  }

  /**
   * Returns the time at which the account was created.
   *
   * @return Milliseconds since epoch timestamp.
   */
  public long getCreationTimestamp() {
    return creationTimestamp;
  }

  /**
   * Returns the time at which the user last signed in.
   *
   * @return Milliseconds since epoch timestamp, or 0 if the user has never signed in.
   */
  public long getLastSignInTimestamp() {
    return lastSignInTimestamp;
  }
}
