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

/**
 * Contains metadata regarding how a user is known by a particular identity provider (IdP).
 * Instances of this class are immutable and thread safe.
 */
public class ProviderUserInfo {

  private final String uid;
  private final String displayName;
  private final String email;
  private final String photoUrl;
  private final String providerId;

  ProviderUserInfo(GetAccountInfoResponse.Provider response) {
    this.uid = response.getUid();
    this.displayName = response.getDisplayName();
    this.email = response.getEmail();
    this.photoUrl = response.getPhotoUrl();
    this.providerId = response.getProviderId();
  }

  /**
   * Returns the user's unique ID at the IdP.
   *
   * @return a user ID string or null.
   */
  public String getUid() {
    return uid;
  }

  /**
   * Returns the user's display name at the IdP
   *
   * @return a display name string or null.
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns the user's email address at the IdP.
   *
   * @return an email address string or null.
   */
  public String getEmail() {
    return email;
  }

  /**
   * Returns the user's photo URL at the IdP.
   *
   * @return a URL string or null.
   */
  public String getPhotoUrl() {
    return photoUrl;
  }

  /**
   * Returns the ID of the identity provider. This can be a short domain name (e.g. google.com) or
   * the OP identifier of an OpenID IdP.
   *
   * @return an ID string that uniquely identifies the IdP.
   */
  public String getProviderId() {
    return providerId;
  }
}
