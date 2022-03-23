/*
 * Copyright 2022 Google LLC
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

package com.google.firebase.appcheck;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * A decoded and verified Firebase App Check token. See {@link FirebaseAppCheck#verifyToken(String)}
 * for details on how to obtain an instance of this class.
 */
public final class DecodedAppCheckToken {

  private final Map<String, Object> claims;

  DecodedAppCheckToken(Map<String, Object> claims) {
    checkArgument(claims != null && claims.containsKey("sub"),
            "Claims map must at least contain sub");
    this.claims = ImmutableMap.copyOf(claims);
  }

  /** Returns the Subject for this token. */
  public String getSubject() {
    return (String) claims.get("sub");
  }

  /** Returns the Issuer for this token. */
  public String getIssuer() {
    return (String) claims.get("iss");
  }

  /** Returns the Audience for this token. */
  public String getAudience() {
    return (String) claims.get("aud");
  }

  /** Returns the Expiration Time for this token. */
  public String getExpirationTime() {
    return (String) claims.get("exp");
  }

  /** Returns the Issued At for this token. */
  public String getIssuedAt() {
    return (String) claims.get("iat");
  }

  /** Returns a map of all the claims on this token. */
  public Map<String, Object> getClaims() {
    return this.claims;
  }
}
