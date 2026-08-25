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

package com.google.firebase.appcheck;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Represents a verified Firebase App Check token.
 */
public class DecodedAppCheckToken {

  private final Map<String, Object> claims;

  /**
   * Creates an instance of {@link DecodedAppCheckToken} from a map of JWT claims.
   *
   * @param claims A map of JWT claims.
   */
  public DecodedAppCheckToken(Map<String, Object> claims) {
    checkNotNull(claims, "Claims map must not be null");
    checkArgument(claims.containsKey("sub"), "Claims map must contain sub");
    this.claims = ImmutableMap.copyOf(claims);
  }

  /**
   * Returns the issuer identifier for the token.
   */
  public String getIssuer() {
    return (String) claims.get("iss");
  }

  /**
   * Returns the subject claim ('sub') of the token.
   */
  public String getSubject() {
    return (String) claims.get("sub");
  }

  /**
   * Returns the audience for which this token is intended.
   */
  public List<String> getAudience() {
    Object audience = claims.get("aud");
    if (audience instanceof String) {
      return ImmutableList.of((String) audience);
    } else if (audience instanceof List) {
      @SuppressWarnings("unchecked")
      List<String> audienceList = (List<String>) audience;
      return ImmutableList.copyOf(audienceList);
    }
    return ImmutableList.of();
  }

  /**
   * Returns the expiration time as an {@link Instant}, or {@code null} if not present.
   */
  public Instant getExpirationTime() {
    return toInstant(claims.get("exp"));
  }

  /**
   * Returns the issued-at time as an {@link Instant}, or {@code null} if not present.
   */
  public Instant getIssuedAt() {
    return toInstant(claims.get("iat"));
  }

  /**
   * Returns the entire map of claims.
   */
  public Map<String, Object> getClaims() {
    return claims;
  }

  private static Instant toInstant(Object timeObj) {
    if (timeObj instanceof Date) {
      return ((Date) timeObj).toInstant();
    }
    if (timeObj instanceof Number) {
      return Instant.ofEpochSecond(((Number) timeObj).longValue());
    }
    return null;
  }
}
