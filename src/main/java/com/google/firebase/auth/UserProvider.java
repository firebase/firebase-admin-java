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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;

/**
 * Represents a user identity provider that can be associated with a Firebase user.
 */
public final class UserProvider {

  @Key("rawId")
  private final String uid;

  @Key("displayName")
  private final String displayName;

  @Key("email")
  private final String email;

  @Key("photoUrl")
  private final String photoUrl;

  @Key("providerId")
  private final String providerId;

  private UserProvider(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.uid), "Uid must not be null or empty");
    checkArgument(!Strings.isNullOrEmpty(builder.providerId),
        "ProviderId must not be null or empty");
    this.uid = builder.uid;
    this.displayName = builder.displayName;
    this.email = builder.email;
    this.photoUrl = builder.photoUrl;
    this.providerId = builder.providerId;
  }

  /**
   * Creates a new {@link UserProvider.Builder}.
   *
   * @return A {@link UserProvider.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String uid;
    private String displayName;
    private String email;
    private String photoUrl;
    private String providerId;

    private Builder() {}

    /**
     * Sets the user's unique ID assigned by the identity provider. This field is required.
     *
     * @param uid a user ID string.
     * @return This builder.
     */
    public Builder setUid(String uid) {
      this.uid = uid;
      return this;
    }

    /**
     * Sets the user's display name.
     *
     * @param displayName display name of the user.
     * @return This builder.
     */
    public Builder setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * Sets the user's email address.
     *
     * @param email an email address string.
     * @return This builder.
     */
    public Builder setEmail(String email) {
      this.email = email;
      return this;
    }

    /**
     * Sets the photo URl of the user.
     *
     * @param photoUrl a photo URL string.
     * @return This builder.
     */
    public Builder setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
      return this;
    }

    /**
     * Sets the ID of the identity provider. This can be a short domain name (e.g. google.com) or
     * the identifier of an OpenID identity provider. This field is required.
     *
     * @param providerId an ID string that uniquely identifies the identity provider.
     * @return This builder.
     */
    public Builder setProviderId(String providerId) {
      this.providerId = providerId;
      return this;
    }

    /**
     * Builds a new {@link UserProvider}.
     *
     * @return A non-null {@link UserProvider}.
     */
    public UserProvider build() {
      return new UserProvider(this);
    }
  }
}
