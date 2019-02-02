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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * A decoded and verified Firebase token. It can used to get the uid and other user attributes
 * available in the token. See {@link FirebaseAuth#verifyIdToken(String)} and
 * {@link FirebaseAuth#verifySessionCookie(String)} for details on how to obtain an instance of
 * this class.
 */
public final class FirebaseToken {

  private final String uid;
  private final String issuer;
  private final String name;
  private final String picture;
  private final String email;
  private final boolean emailVerified;
  private final Map<String, Object> claims;

  private FirebaseToken(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.uid));
    this.uid = builder.uid;
    this.issuer = builder.issuer;
    this.name = builder.name;
    this.picture = builder.picture;
    this.email = builder.email;
    this.emailVerified = builder.emailVerified;
    this.claims = builder.claims != null ? ImmutableMap.copyOf(builder.claims)
        : ImmutableMap.<String, Object>of();
  }

  /** Returns the Uid for the this token. */
  public String getUid() {
    return this.uid;
  }

  /** Returns the Issuer for the this token. */
  public String getIssuer() {
    return this.issuer;
  }

  /** Returns the user's display name. */
  public String getName() {
    return this.name;
  }

  /** Returns the Uri string of the user's profile photo. */
  public String getPicture() {
    return this.picture;
  }

  /** 
   * Returns the e-mail address for this user, or {@code null} if it's unavailable.
   */
  public String getEmail() {
    return this.email;
  }

  /** 
   * Indicates if the email address returned by {@link #getEmail()} has been verified as good.
   */
  public boolean isEmailVerified() {
    return this.emailVerified;
  }

  /** Returns a map of all of the claims on this token. */
  public Map<String, Object> getClaims() {
    return this.claims;
  }

  static class Builder {
    private String uid;
    private String issuer;
    private String name;
    private String picture;
    private String email;
    private boolean emailVerified;
    private Map<String, Object> claims;

    Builder setUid(String uid) {
      this.uid = uid;
      return this;
    }

    Builder setIssuer(String issuer) {
      this.issuer = issuer;
      return this;
    }

    Builder setName(String name) {
      this.name = name;
      return this;
    }

    Builder setPicture(String picture) {
      this.picture = picture;
      return this;
    }

    Builder setEmail(String email) {
      this.email = email;
      return this;
    }

    Builder setEmailVerified(boolean emailVerified) {
      this.emailVerified = emailVerified;
      return this;
    }

    Builder setClaims(Map<String, Object> claims) {
      this.claims = claims;
      return this;
    }

    FirebaseToken build() {
      return new FirebaseToken(this);
    }
  }
}
