/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

public class FirebaseTokenTest {

  @Test
  public void testFirebaseToken() {
    Map<String, Object> claims = ImmutableMap.<String, Object>builder()
        .put("sub", "testUser")
        .put("iss", "test-project-id")
        .put("email", "test@example.com")
        .put("email_verified", true)
        .put("name", "Test User")
        .put("picture", "https://picture.url")
        .put("custom", "claim")
        .build();

    FirebaseToken firebaseToken = new FirebaseToken(claims);

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("test-project-id", firebaseToken.getIssuer());
    assertEquals("test@example.com", firebaseToken.getEmail());
    assertTrue(firebaseToken.isEmailVerified());
    assertEquals("Test User", firebaseToken.getName());
    assertEquals("https://picture.url", firebaseToken.getPicture());
    assertEquals("claim", firebaseToken.getClaims().get("custom"));
    assertEquals(7, firebaseToken.getClaims().size());
  }

  @Test
  public void testFirebaseTokenMinimal() {
    Map<String, Object> claims = ImmutableMap.<String, Object>builder()
        .put("sub", "testUser")
        .build();

    FirebaseToken firebaseToken = new FirebaseToken(claims);

    assertEquals("testUser", firebaseToken.getUid());
    assertNull(firebaseToken.getIssuer());
    assertNull(firebaseToken.getEmail());
    assertFalse(firebaseToken.isEmailVerified());
    assertNull(firebaseToken.getName());
    assertNull(firebaseToken.getPicture());
    assertEquals(1, firebaseToken.getClaims().size());
  }

  @Test
  public void testFirebaseTokenFromIdToken() {
    IdToken.Payload payload = new IdToken.Payload()
        .setSubject("testUser")
        .setIssuer("test-project-id")
        .set("email", "test@example.com")
        .set("email_verified", true)
        .set("name", "Test User")
        .set("picture", "https://picture.url")
        .set("custom", "claim");

    FirebaseToken firebaseToken = new FirebaseToken(payload);

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("test-project-id", firebaseToken.getIssuer());
    assertEquals("test@example.com", firebaseToken.getEmail());
    assertTrue(firebaseToken.isEmailVerified());
    assertEquals("Test User", firebaseToken.getName());
    assertEquals("https://picture.url", firebaseToken.getPicture());
    assertEquals("claim", firebaseToken.getClaims().get("custom"));
    assertEquals(7, firebaseToken.getClaims().size());
  }

  @Test
  public void testFirebaseTokenFromMinimalIdToken() {
    IdToken.Payload payload = new IdToken.Payload()
        .setSubject("testUser");

    FirebaseToken firebaseToken = new FirebaseToken(payload);

    assertEquals("testUser", firebaseToken.getUid());
    assertNull(firebaseToken.getIssuer());
    assertNull(firebaseToken.getEmail());
    assertFalse(firebaseToken.isEmailVerified());
    assertNull(firebaseToken.getName());
    assertNull(firebaseToken.getPicture());
    assertEquals(1, firebaseToken.getClaims().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseTokenNullClaims() {
    new FirebaseToken(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseTokenNoUid() {
    ImmutableMap<String, Object> claimsWithoutSub = ImmutableMap.<String, Object>of(
        "custom", "claim");
    new FirebaseToken(claimsWithoutSub);
  }
}
