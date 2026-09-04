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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class DecodedAppCheckTokenTest {

  private static final String APP_ID = "test-app-id";
  private static final String ISSUER = "https://firebaseappcheck.googleapis.com/";
  private static final String AUDIENCE = "projects/test-project-id";

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testNullClaims_ThrowsException() {
    NullPointerException e =
        assertThrows(NullPointerException.class, () -> new DecodedAppCheckToken(null));
    assertTrue(e.getMessage().contains("Claims map must not be null"));
  }

  @Test
  public void testMissingSub_ThrowsException() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("iss", ISSUER);

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new DecodedAppCheckToken(claims));
    assertTrue(e.getMessage().contains("Claims map must contain sub"));
  }

  @Test
  public void testGetters_WithDateTimestamps() {
    Date iat = new Date(1600000000000L);
    Date exp = new Date(1600003600000L);

    Map<String, Object> claims =
        ImmutableMap.<String, Object>builder()
            .put("sub", APP_ID)
            .put("iss", ISSUER)
            .put("aud", AUDIENCE)
            .put("iat", iat)
            .put("exp", exp)
            .put("jti", "test-jwt-id")
            .put("provider", "play_integrity")
            .build();

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);

    assertNotNull(token);
    assertEquals(APP_ID, token.getSubject());
    assertEquals(ISSUER, token.getIssuer());
    assertEquals("test-jwt-id", token.getJti());
    assertEquals("play_integrity", token.getProvider());
    assertEquals(ImmutableList.of(AUDIENCE), token.getAudience());
    assertEquals(Instant.ofEpochMilli(1600000000000L), token.getIssuedAt());
    assertEquals(Instant.ofEpochMilli(1600003600000L), token.getExpirationTime());
    assertEquals(claims, token.getClaims());
  }

  @Test
  public void testGetters_WithNumericTimestamps() {
    Map<String, Object> claims =
        ImmutableMap.<String, Object>builder()
            .put("sub", APP_ID)
            .put("iat", 1600000000L)
            .put("exp", 1600003600L)
            .build();

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);

    assertNotNull(token);
    assertEquals(APP_ID, token.getSubject());
    assertNull(token.getJti());
    assertNull(token.getProvider());
    assertEquals(Instant.ofEpochSecond(1600000000L), token.getIssuedAt());
    assertEquals(Instant.ofEpochSecond(1600003600L), token.getExpirationTime());
  }

  @Test
  public void testGetAudience_ListFormat() {
    Map<String, Object> claims =
        ImmutableMap.<String, Object>of(
            "sub", APP_ID,
            "aud", ImmutableList.of("aud1", "aud2"));

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);

    assertEquals(ImmutableList.of("aud1", "aud2"), token.getAudience());
  }

  @Test
  public void testGetAudience_StringFormat() {
    Map<String, Object> claims =
        ImmutableMap.<String, Object>of(
            "sub", APP_ID,
            "aud", AUDIENCE);

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);

    assertEquals(ImmutableList.of(AUDIENCE), token.getAudience());
  }

  @Test
  public void testGetAudience_EmptyList() {
    Map<String, Object> claims =
        ImmutableMap.<String, Object>of(
            "sub", APP_ID,
            "aud", ImmutableList.of());

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);

    assertEquals(ImmutableList.of(), token.getAudience());
  }

  @Test
  public void testGetAudience_EmptyWhenMissingOrUnknownType() {
    Map<String, Object> claims = ImmutableMap.<String, Object>of("sub", APP_ID);
    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);
    assertEquals(ImmutableList.of(), token.getAudience());
  }

  @Test
  public void testTimestamps_NullWhenMissingOrUnknownType() {
    Map<String, Object> claims = ImmutableMap.<String, Object>of("sub", APP_ID);
    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);
    assertNull(token.getIssuedAt());
    assertNull(token.getExpirationTime());
  }

  @Test
  public void testClaims_WithNullValues_DoesNotThrow() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", APP_ID);
    claims.put("custom_null_claim", null);

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);
    assertNotNull(token);
    assertEquals(APP_ID, token.getSubject());
    assertTrue(token.getClaims().containsKey("custom_null_claim"));
    assertNull(token.getClaims().get("custom_null_claim"));
  }

  @Test
  public void testClaims_Unmodifiable() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", APP_ID);

    DecodedAppCheckToken token = new DecodedAppCheckToken(claims);
    assertThrows(
        UnsupportedOperationException.class, () -> token.getClaims().put("new_key", "value"));
  }
}
