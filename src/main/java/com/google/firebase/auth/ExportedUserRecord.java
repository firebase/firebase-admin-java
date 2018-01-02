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

import com.google.api.client.json.JsonFactory;
import com.google.firebase.auth.internal.DownloadAccountResponse.User;
import com.google.firebase.internal.Nullable;

/**
 * Contains metadata associated with a Firebase user account, along with password hash and salt.
 * Instances of this class are immutable and thread-safe.
 */
public class ExportedUserRecord extends UserRecord {

  private final String passwordHash;
  private final String passwordSalt;

  ExportedUserRecord(User response, JsonFactory jsonFactory) {
    super(response, jsonFactory);
    this.passwordHash = response.getPasswordHash();
    this.passwordSalt = response.getPasswordSalt();
  }

  /**
   * Returns the user's password hash as a base64-encoded string.
   *
   * <p>If the Firebase Auth hashing algorithm (SCRYPT) was used to create the user account,
   * returns the base64-encoded password hash of the user. If a different hashing algorithm was
   * used to create this user, as is typical when migrating from another Auth system, returns
   * an empty string. Returns null if no password is set.
   *
   * @return A base64-encoded password hash, possibly empty or null.
   */
  @Nullable
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * Returns the user's password salt as a base64-encoded string.
   *
   * <p>If the Firebase Auth hashing algorithm (SCRYPT) was used to create the user account,
   * returns the base64-encoded password salt of the user. If a different hashing algorithm was
   * used to create this user, as is typical when migrating from another Auth system, returns
   * an empty string. Returns null if no password is set.
   *
   * @return A base64-encoded password salt, possibly empty or null.
   */
  @Nullable
  public String getPasswordSalt() {
    return passwordSalt;
  }
}
