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
import java.net.MalformedURLException;
import java.net.URL;

public final class NewAccount {

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

  @Key("password")
  private String password;

  private NewAccount(Builder builder) {
    uid = builder.uid;
    displayName = builder.displayName;
    email = builder.email;
    password = builder.password;
    photoUrl = builder.photoUrl;
    emailVerified = builder.emailVerified;
    disabled = builder.disabled;

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
  }

  public static class Builder {
    private String uid;
    private String email;
    private boolean emailVerified;
    private String displayName;
    private String photoUrl;
    private boolean disabled;
    private String password;

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

    public Builder setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
      return this;
    }

    public Builder setDisabled(boolean disabled) {
      this.disabled = disabled;
      return this;
    }

    public Builder setPassword(String password) {
      this.password = password;
      return this;
    }

    public NewAccount build() {
      return new NewAccount(this);
    }
  }

}
