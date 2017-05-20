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

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class UpdateAccount {

  private static List<String> DELETABLE_FIELDS = ImmutableList.of("DISPLAY_NAME", "PHOTO_URL");

  @Key("localId")
  private final String uid;

  @Key("email")
  private String email;

  @Key("emailVerified")
  private boolean emailVerified;

  @Key("displayName")
  private String displayName;

  @Key("photoUrl")
  private String photoUrl;

  @Key("disableUser")
  private boolean disabled;

  @Key("password")
  private String password;

  @Key("deleteAttribute")
  private List<String> deleteAttribute = new ArrayList<>();

  private UpdateAccount(Builder builder) {
    uid = builder.user.getUid();
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

    private final User user;
    private String email;
    private boolean emailVerified;
    private String displayName;
    private String photoUrl;
    private boolean disabled;
    private String password;

    Builder(User user) {
      checkNotNull(user, "User argument must not be null");
      checkArgument(!Strings.isNullOrEmpty(user.getUid()), "UID must not be null or empty");
      this.user = user;
      this.email = user.getEmail();
      this.emailVerified = user.isEmailVerified();
      this.displayName = user.getDisplayName();
      this.photoUrl = user.getPhotoUrl();
      this.disabled = user.isDisabled();
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
  }

}
