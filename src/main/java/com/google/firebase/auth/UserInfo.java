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

import com.google.firebase.internal.Nullable;

/**
 * A collection of standard profile information for a user. Used to expose profile information
 * returned by an identity provider.
 */
public interface UserInfo {

  /**
   * Returns the user's unique ID assigned by the identity provider.
   *
   * @return a user ID string.
   */
  String getUid();

  /**
   * Returns the user's display name, if available.
   *
   * @return a display name string or null.
   */
  @Nullable
  String getDisplayName();

  /**
   * Returns the user's email address, if available.
   *
   * @return an email address string or null.
   */
  @Nullable
  String getEmail();

  /**
   * Returns the user's phone number, if available.
   *
   * @return a phone number string or null.
   */
  @Nullable
  String getPhoneNumber();

  /**
   * Returns the user's photo URL, if available.
   *
   * @return a URL string or null.
   */
  @Nullable
  String getPhotoUrl();

  /**
   * Returns the ID of the identity provider. This can be a short domain name (e.g. google.com) or
   * the identifier of an OpenID identity provider.
   *
   * @return an ID string that uniquely identifies the identity provider.
   */
  String getProviderId();

}
