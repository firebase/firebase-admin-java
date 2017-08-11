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

package com.google.firebase.auth.internal;

import com.google.api.client.util.Key;
import java.util.List;

/**
 * JSON data binding for getAccountInfoResponse messages sent by Google identity toolkit service.
 */
public final class GetAccountInfoResponse {

  @Key("kind")
  private String kind;

  @Key("users")
  private List<User> users;

  public String getKind() {
    return kind;
  }

  public List<User> getUsers() {
    return users;
  }

  /**
   * JSON data binding for user records.
   */
  public static final class User {

    @Key("localId")
    private String uid;

    @Key("email")
    private String email;

    @Key("phoneNumber")
    private String phoneNumber;

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

    public String getPhoneNumber() {
      return phoneNumber;
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
  }

  /**
   * JSON data binding for provider data.
   */
  public static final class Provider {

    @Key("rawId")
    private String uid;

    @Key("displayName")
    private String displayName;

    @Key("email")
    private String email;

    @Key("phoneNumber")
    private String phoneNumber;

    @Key("photoUrl")
    private String photoUrl;

    @Key("providerId")
    private String providerId;

    public String getUid() {
      return uid;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getEmail() {
      return email;
    }

    public String getPhoneNumber() {
      return phoneNumber;
    }

    public String getPhotoUrl() {
      return photoUrl;
    }

    public String getProviderId() {
      return providerId;
    }
  }
}
