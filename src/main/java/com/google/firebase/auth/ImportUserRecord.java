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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.internal.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a user account to be imported to Firebase Auth via the
 * {@link FirebaseAuth#importUsers(List, UserImportOptions)} API. Must contain at least a
 * uid string.
 */
public final class ImportUserRecord {

  private final Map<String, Object> properties;

  private ImportUserRecord(Map<String, Object> properties) {
    this.properties = ImmutableMap.copyOf(properties);
  }

  Map<String, Object> getProperties(JsonFactory jsonFactory) {
    Map<String, Object> copy = new HashMap<>(properties);
    // serialize custom claims
    if (copy.containsKey(UserRecord.CUSTOM_ATTRIBUTES)) {
      Map customClaims = (Map) copy.remove(UserRecord.CUSTOM_ATTRIBUTES);
      copy.put(UserRecord.CUSTOM_ATTRIBUTES, UserRecord.serializeCustomClaims(
          customClaims, jsonFactory));
    }
    return ImmutableMap.copyOf(copy);
  }

  boolean hasPassword() {
    return this.properties.containsKey("passwordHash");
  }

  /**
   * Creates a new {@link ImportUserRecord.Builder}.
   *
   * @return A {@link ImportUserRecord.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String uid;
    private String email;
    private Boolean emailVerified;
    private String displayName;
    private String phoneNumber;
    private String photoUrl;
    private Boolean disabled;
    private UserMetadata userMetadata;
    private byte[] passwordHash;
    private byte[] passwordSalt;

    private final List<UserProvider> userProviders = new ArrayList<>();
    private final Map<String, Object> customClaims = new HashMap<>();

    private Builder() {}

    /**
     * Sets a user ID for the user.
     *
     * @param uid a non-null, non-empty user ID that uniquely identifies the user. The user ID
     *     must not be longer than 128 characters.
     * @return This builder.
     */
    public Builder setUid(String uid) {
      this.uid = uid;
      return this;
    }

    /**
     * Sets an email address for the user.
     *
     * @param email a non-null, non-empty email address string.
     * @return This builder.
     */
    public Builder setEmail(String email) {
      this.email = email;
      return this;
    }

    /**
     * Sets whether the user email address has been verified or not.
     *
     * @param emailVerified a boolean indicating the email verification status.
     * @return This builder.
     */
    public Builder setEmailVerified(boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    /**
     * Sets the display name for the user.
     *
     * @param displayName a non-null, non-empty display name string.
     * @return This builder.
     */
    public Builder setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * Sets the phone number associated with this user.
     *
     * @param phoneNumber a valid phone number string.
     * @return This builder.
     */
    public Builder setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }

    /**
     * Sets the photo URL for the user.
     *
     * @param photoUrl a non-null, non-empty URL string.
     * @return This builder.
     */
    public Builder setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
      return this;
    }

    /**
     * Sets whether the user account should be disabled by default or not.
     *
     * @param disabled a boolean indicating whether the account should be disabled.
     * @return This builder.
     */
    public Builder setDisabled(boolean disabled) {
      this.disabled = disabled;
      return this;
    }

    /**
     * Sets additional metadata about the user.
     *
     * @param userMetadata A {@link UserMetadata} instance.
     * @return This builder.
     */
    public Builder setUserMetadata(UserMetadata userMetadata) {
      this.userMetadata = userMetadata;
      return this;
    }

    /**
     * Sets a byte array representing the user's hashed password. If at least one user account
     * carries a password hash, a {@link UserImportHash} must be specified when calling the
     * {@link FirebaseAuth#importUsersAsync(List, UserImportOptions)} method. See
     * {@link UserImportOptions.Builder#setHash(UserImportHash)}.
     *
     * @param passwordHash A byte array.
     * @return This builder.
     */
    public Builder setPasswordHash(byte[] passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    /**
     * Sets a byte array representing the user's password salt.
     *
     * @param passwordSalt A byte array.
     * @return This builder.
     */
    public Builder setPasswordSalt(byte[] passwordSalt) {
      this.passwordSalt = passwordSalt;
      return this;
    }

    /**
     * Adds a user provider to be associated with this user.
     *
     * <p>A {@link UserProvider} represents the identity of the user as specified by an
     * identity provider that is linked to this user account. The identity provider can specify
     * its own values for common user attributes like email, display name and photo URL.
     *
     * @param provider A non-null {@link UserProvider}.
     * @return This builder.
     */
    public Builder addUserProvider(@NonNull UserProvider provider) {
      this.userProviders.add(provider);
      return this;
    }

    /**
     * Associates all user provider's in the given list with this user.
     *
     * @param providers A list of {@link UserProvider} instances.
     * @return This builder.
     */
    public Builder addAllUserProviders(List<UserProvider> providers) {
      this.userProviders.addAll(providers);
      return this;
    }

    /**
     * Sets the specified custom claim on this user account.
     *
     * @param key Name of the claim.
     * @param value Value of the claim.
     * @return This builder.
     */
    public Builder putCustomClaim(String key, Object value) {
      this.customClaims.put(key, value);
      return this;
    }

    /**
     * Sets the custom claims associated with this user.
     *
     * @param customClaims a Map of custom claims
     */
    public Builder putAllCustomClaims(Map<String, Object> customClaims) {
      this.customClaims.putAll(customClaims);
      return this;
    }

    /**
     * Builds a new {@link ImportUserRecord}.
     *
     * @return A non-null {@link ImportUserRecord}.
     */
    public ImportUserRecord build() {
      Map<String, Object> properties = new HashMap<>();
      UserRecord.checkUid(uid);
      properties.put("localId", uid);

      if (!Strings.isNullOrEmpty(email)) {
        UserRecord.checkEmail(email);
        properties.put("email", email);
      }
      if (!Strings.isNullOrEmpty(photoUrl)) {
        UserRecord.checkUrl(photoUrl);
        properties.put("photoUrl", photoUrl);
      }
      if (!Strings.isNullOrEmpty(phoneNumber)) {
        UserRecord.checkPhoneNumber(phoneNumber);
        properties.put("phoneNumber", phoneNumber);
      }
      if (!Strings.isNullOrEmpty(displayName)) {
        properties.put("displayName", displayName);
      }
      if (userMetadata != null) {
        if (userMetadata.getCreationTimestamp() > 0) {
          properties.put("createdAt", userMetadata.getCreationTimestamp());
        }
        if (userMetadata.getLastSignInTimestamp() > 0) {
          properties.put("lastLoginAt", userMetadata.getLastSignInTimestamp());
        }
      }
      if (passwordHash != null) {
        properties.put("passwordHash", BaseEncoding.base64Url().encode(passwordHash));
      }
      if (passwordSalt != null) {
        properties.put("salt", BaseEncoding.base64Url().encode(passwordSalt));
      }
      if (userProviders.size() > 0) {
        properties.put("providerUserInfo", ImmutableList.copyOf(userProviders));
      }
      if (customClaims.size() > 0) {
        ImmutableMap<String, Object> mergedClaims = ImmutableMap.copyOf(customClaims);
        UserRecord.checkCustomClaims(mergedClaims);
        properties.put(UserRecord.CUSTOM_ATTRIBUTES, mergedClaims);
      }
      if (emailVerified != null) {
        properties.put("emailVerified", emailVerified);
      }
      if (disabled != null) {
        properties.put("disabled", disabled);
      }
      return new ImportUserRecord(properties);
    }
  }
}
