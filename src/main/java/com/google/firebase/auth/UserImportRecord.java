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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserImportRecord {

  private final Map<String, Object> properties;

  private UserImportRecord(Map<String, Object> properties) {
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

    public Builder setUid(String uid) {
      this.uid = uid;
      return this;
    }

    public Builder setEmail(String email) {
      this.email = email;
      return this;
    }

    public Builder setEmailVerified(boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    public Builder setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
      return this;
    }

    public Builder setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
      return this;
    }

    public Builder setDisabled(boolean disabled) {
      this.disabled = disabled;
      return this;
    }

    public Builder setUserMetadata(UserMetadata userMetadata) {
      this.userMetadata = userMetadata;
      return this;
    }

    public Builder setPasswordHash(byte[] passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    public Builder setPasswordSalt(byte[] passwordSalt) {
      this.passwordSalt = passwordSalt;
      return this;
    }

    public Builder addUserProvider(UserProvider provider) {
      this.userProviders.add(provider);
      return this;
    }

    public Builder addAllUserProviders(List<UserProvider> providers) {
      this.userProviders.addAll(providers);
      return this;
    }

    public Builder putCustomClaim(String key, Object value) {
      this.customClaims.put(key, value);
      return this;
    }

    public Builder putAllCustomClaims(Map<String, Object> claims) {
      this.customClaims.putAll(claims);
      return this;
    }

    public UserImportRecord build() {
      Map<String, Object> properties = new HashMap<>();
      UserRecord.checkUid(uid);
      properties.put("localId", uid);

      if (!Strings.isNullOrEmpty(email)) {
        UserRecord.checkEmail(email);
        properties.put("email", email);
      }
      if (!Strings.isNullOrEmpty(photoUrl)) {
        UserRecord.checkPhotoUrl(photoUrl);
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
        properties.put(UserRecord.CUSTOM_ATTRIBUTES, ImmutableMap.copyOf(customClaims));
      }
      if (emailVerified != null) {
        properties.put("emailVerified", emailVerified);
      }
      if (disabled != null) {
        properties.put("disabled", disabled);
      }
      return new UserImportRecord(properties);
    }
  }
}
