/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.internal.NonNull;
import java.util.Set;

/**
 * Represents the result of the {@link FirebaseAuth#getUsersAsync(Collection)} API.
 */
public final class GetUsersResult {
  private final Set<UserRecord> users;
  private final Set<UserIdentifier> notFound;

  GetUsersResult(@NonNull Set<UserRecord> users, @NonNull Set<UserIdentifier> notFound) {
    this.users = checkNotNull(users);
    this.notFound = checkNotNull(notFound);
  }

  /**
   * Set of user records corresponding to the set of users that were requested. Only users
   * that were found are listed here. The result set is unordered.
   */
  @NonNull
  public Set<UserRecord> getUsers() {
    return this.users;
  }

  /**
   * Set of identifiers that were requested, but not found.
   */
  @NonNull
  public Set<UserIdentifier> getNotFound() {
    return this.notFound;
  }
}
