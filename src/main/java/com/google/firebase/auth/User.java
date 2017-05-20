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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.util.Key;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class User {

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

  @Key("password")
  private String password;

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

  @Override
  public String toString() {
    try {
      return Utils.getDefaultJsonFactory().toPrettyString(this);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class NewAccount {
    private String uid;
    private String email;
    private boolean emailVerified;
    private String password;
    private String displayName;
    private String photoUrl;
    private boolean disabled;

    public NewAccount setUid(String uid) {
      this.uid = uid;
      return this;
    }

    public NewAccount setEmail(String email) {
      this.email = email;
      return this;
    }

    public NewAccount setEmailVerified(boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    public NewAccount setPassword(String password) {
      this.password = password;
      return this;
    }

    public NewAccount setDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public NewAccount setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
      return this;
    }

    public NewAccount setDisabled(boolean disabled) {
      this.disabled = disabled;
      return this;
    }

    User build() {
      if (uid != null) {
        checkArgument(!uid.isEmpty(), "UID must not be empty");
        checkArgument(uid.length() <= 128, "UID must not be longer than 128 characters");
      }
      if (password != null) {
        checkArgument(password.length() >= 6,
            "Password must be at least 6 characters long");
      }
      if (email != null) {
        checkArgument(email.matches("^[^@]+@[^@]+$"), "Malformed email address");
      }
      if (photoUrl != null) {
        try {
          new URL(photoUrl);
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("Malformed photo URL", e);
        }
      }
      User user = new User();
      user.uid = uid;
      user.displayName = displayName;
      user.email = email;
      user.password = password;
      user.photoUrl = photoUrl;
      user.emailVerified = emailVerified;
      user.disabled = disabled;
      return user;
    }
  }
}
