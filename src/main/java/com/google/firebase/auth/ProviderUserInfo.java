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

import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.internal.Nullable;

/**
 * Contains metadata regarding how a user is known by a particular identity provider (IdP).
 * Instances of this class are immutable and thread safe.
 */
class ProviderUserInfo implements UserInfo {

  private final String uid;
  private final String displayName;
  private final String email;
  private final String phoneNumber;
  private final String photoUrl;
  private final String providerId;

  ProviderUserInfo(GetAccountInfoResponse.Provider response) {
    this.uid = response.getUid();
    this.displayName = response.getDisplayName();
    this.email = response.getEmail();
    this.phoneNumber = response.getPhoneNumber();
    this.photoUrl = response.getPhotoUrl();
    this.providerId = response.getProviderId();
  }

  @Override
  public String getUid() {
    return uid;
  }

  @Nullable
  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Nullable
  @Override
  public String getEmail() {
    return email;
  }

  @Nullable
  @Override
  public String getPhoneNumber() {
    return phoneNumber;
  }

  @Nullable
  @Override
  public String getPhotoUrl() {
    return photoUrl;
  }

  @Override
  public String getProviderId() {
    return providerId;
  }
}
