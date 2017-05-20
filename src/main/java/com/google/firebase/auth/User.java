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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {

  private static final Map<String, String> REMOVABLE_FIELDS = ImmutableMap.of(
      "displayName", "DISPLAY_NAME",
      "photoUrl", "PHOTO_URL");

  @Key("localId")
  private String uid;

  @Key("email")
  private String email;

  @Key("emailVerified")
  private boolean emailVerified;

  @Key("displayName")
  private String displayName;

  @Key("photoUrl")
  private String photoUrl;

  @Key("disabled")
  private boolean disabled;

  @Key("providerUserInfo")
  private Provider[] providers;

  @Key("createdAt")
  private long createdAt;

  @Key("lastLoginAt")
  private long lastLoginAt;

  public String getUid() {
    return uid;
  }

  public String getEmail() {
    return email;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public Provider[] getProviders() {
    return providers;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public long getLastLoginAt() {
    return lastLoginAt;
  }

  public Updater updater() {
    return new Updater(this);
  }

  public User() {
  }

  @VisibleForTesting
  User(String uid) {
    this.uid = uid;
  }

  @Override
  public String toString() {
    try {
      return Utils.getDefaultJsonFactory().toPrettyString(this);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  private static void checkEmail(String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email cannot be null or empty");
    checkArgument(email.matches("^[^@]+@[^@]+$"));
  }

  private static void checkPassword(String password) {
    checkArgument(!Strings.isNullOrEmpty(password), "password cannot be null or empty");
    checkArgument(password.length() >= 6, "password must be at least 6 characters long");
  }

  public static class Builder {

    private final Map<String,Object> properties = new HashMap<>();

    private Builder() {
    }

    public Builder setUid(String uid) {
      checkArgument(!Strings.isNullOrEmpty(uid), "uid cannot be null or empty");
      checkArgument(uid.length() <= 128, "UID cannot be longer than 128 characters");
      properties.put("localId", uid);
      return this;
    }

    public Builder setEmail(String email) {
      checkEmail(email);
      properties.put("email", email);
      return this;
    }

    public Builder setEmailVerified(boolean emailVerified) {
      properties.put("emailVerified", emailVerified);
      return this;
    }

    public Builder setDisplayName(String displayName) {
      checkNotNull(displayName, "displayName cannot be null or empty");
      properties.put("displayName", displayName);
      return this;
    }

    public Builder setPhotoUrl(String photoUrl) {
      checkArgument(!Strings.isNullOrEmpty(photoUrl), "photoUrl cannot be null or empty");
      try {
        new URL(photoUrl);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("malformed photoUrl string", e);
      }
      properties.put("photoUrl", photoUrl);
      return this;
    }

    public Builder setDisabled(boolean disabled) {
      properties.put("disabled", disabled);
      return this;
    }

    public Builder setPassword(String password) {
      checkPassword(password);
      properties.put("password", password);
      return this;
    }

    Map<String, Object> build() {
      return ImmutableMap.copyOf(properties);
    }
  }

  public static class Updater {

    private final Map<String,Object> properties = new HashMap<>();

    private Updater(User user) {
      checkNotNull(user, "user must not be null");
      checkArgument(!Strings.isNullOrEmpty(user.uid), "uid must not be null or empty");
      properties.put("localId", user.uid);
    }

    String getUid() {
      return (String) properties.get("localId");
    }

    public Updater setEmail(String email) {
      checkEmail(email);
      properties.put("email", email);
      return this;
    }

    public Updater setEmailVerified(boolean emailVerified) {
      properties.put("emailVerified", emailVerified);
      return this;
    }

    /**
     * Update the display name of this User. Calling this method with a null argument removes the
     * display name attribute from the user account.
     *
     * @param displayName a display name string or null
     */
    public Updater setDisplayName(String displayName) {
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Update the Photo URL of this User. Calling this method with null a null argument removes
     * the photo URL attribute from the user account.
     *
     * @param photoUrl a valid URL string or null
     */
    public Updater setPhotoUrl(String photoUrl) {
      if (photoUrl != null) {
        try {
          new URL(photoUrl);
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("malformed photoUrl string", e);
        }
      }
      properties.put("photoUrl", photoUrl);
      return this;
    }

    public Updater setDisabled(boolean disabled) {
      properties.put("disableUser", disabled);
      return this;
    }

    public Updater setPassword(String password) {
      checkPassword(password);
      properties.put("password", password);
      return this;
    }

    Map<String, Object> update() {
      Map<String, Object> copy = new HashMap<>(properties);
      List<String> remove = new ArrayList<>();
      for (Map.Entry<String, String> entry : REMOVABLE_FIELDS.entrySet()) {
        if (copy.containsKey(entry.getKey()) && copy.get(entry.getKey()) == null) {
          remove.add(entry.getValue());
          copy.remove(entry.getKey());
        }
      }

      if (!remove.isEmpty()) {
        copy.put("deleteAttribute", ImmutableList.copyOf(remove));
      }
      return ImmutableMap.copyOf(copy);
    }
  }

}
