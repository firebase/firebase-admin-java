/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.fpnv;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a verified Firebase Phone Number Verification token.
 */
public class FirebasePnvToken {
  private final Map<String, Object> claims;

  public FirebasePnvToken(Map<String, Object> claims) {
    // this.claims = claims != null ? Collections.unmodifiableMap(claims) : Collections.emptyMap();
    checkArgument(claims != null && claims.containsKey("sub"),
        "Claims map must at least contain sub");
    this.claims = ImmutableMap.copyOf(claims);
  }

  /**
   * Returns the issuer identifier for the issuer of the response.
   */
  public String getIssuer() {
    return (String) claims.get("iss");
  }

  /**
   * Returns the phone number of the user.
   * This corresponds to the 'sub' claim in the JWT.
   */
  public String getPhoneNumber() {
    return (String) claims.get("sub");
  }

  /**
   * Returns the audience for which this token is intended.
   */
  public List<String> getAudience() {
    Object audience = claims.get("aud");
    if (audience instanceof String) {
      return ImmutableList.of((String) audience);
    }
    return (List<String>) audience;
  }

  /**
   * Returns the expiration time in seconds since the Unix epoch.
   */
  public long getExpirationTime() {
    return ((java.util.Date) claims.get("exp")).getTime();
  }

  /**
   * Returns the issued-at time in seconds since the Unix epoch.
   */
  public long getIssuedAt() {
    return ((java.util.Date) claims.get("iat")).getTime();
  }

  /**
   * Returns the entire map of claims.
   */
  public Map<String, Object> getClaims() {
    return claims;
  }
}