/*
 * Copyright 2018 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.List;
import java.util.Map;

/**
 * A collection of options that can be passed to the
 * {@link FirebaseAuth#importUsersAsync(List, UserImportOptions)} API.
 */
public final class UserImportOptions {

  private final UserImportHash hash;

  private UserImportOptions(Builder builder) {
    this.hash = checkNotNull(builder.hash);
  }

  /**
   * Creates a new {@link UserImportOptions} containing the provided hash algorithm.
   *
   * @param hash A non-null {@link UserImportHash}.
   * @return A new {@link UserImportOptions}.
   */
  public static UserImportOptions withHash(@NonNull UserImportHash hash) {
    return builder().setHash(checkNotNull(hash)).build();
  }

  /**
   * Creates a new {@link UserImportOptions.Builder}.
   *
   * @return A {@link UserImportOptions.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  Map<String, Object> getProperties() {
    return ImmutableMap.<String, Object>builder()
        .putAll(hash.getProperties())
        .build();
  }

  UserImportHash getHash() {
    return hash;
  }

  public static class Builder {

    private UserImportHash hash;

    private Builder() {}

    /**
     * Sets the algorithm used to hash user passwords. This is required
     * when at least one of the {@link ImportUserRecord} instances being imported has a password
     * hash. See {@link ImportUserRecord.Builder#setPasswordHash(byte[])}.
     *
     * @param hash A {@link UserImportHash}.
     * @return This builder.
     */
    public Builder setHash(@NonNull UserImportHash hash) {
      this.hash = hash;
      return this;
    }

    /**
     * Builds a new {@link UserImportOptions}.
     *
     * @return A non-null {@link UserImportOptions}.
     */
    public UserImportOptions build() {
      return new UserImportOptions(this);
    }
  }
}
