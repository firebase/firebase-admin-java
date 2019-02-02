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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Implementation of a Parsed Firebase Token returned by {@link FirebaseAuth#verifyIdToken(String)}.
 * It can used to get the uid and other attributes of the user provided in the Token.
 */
public final class FirebaseToken {

  private final IdToken.Payload tokenPayload;

  FirebaseToken(IdToken token) {
    this.tokenPayload = checkNotNull(token).getPayload();
  }

  /** Returns the Uid for the this token. */
  public String getUid() {
    return tokenPayload.getSubject();
  }

  /** Returns the Issuer for the this token. */
  public String getIssuer() {
    return tokenPayload.getIssuer();
  }

  /** Returns the user's display name. */
  public String getName() {
    return (String) tokenPayload.get("name");
  }

  /** Returns the Uri string of the user's profile photo. */
  public String getPicture() {
    return (String) tokenPayload.get("picture");
  }

  /** 
   * Returns the e-mail address for this user, or {@code null} if it's unavailable.
   */
  public String getEmail() {
    return (String) tokenPayload.get("email");
  }

  /** 
   * Indicates if the email address returned by {@link #getEmail()} has been verified as good.
   */
  public boolean isEmailVerified() {
    Object emailVerified = tokenPayload.get("email_verified");
    return emailVerified != null && (Boolean) emailVerified;
  }

  /** Returns a map of all of the claims on this token. */
  public Map<String, Object> getClaims() {
    return ImmutableMap.copyOf(tokenPayload);
  }
}
