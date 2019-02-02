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

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class FirebaseTokenTest {

  @Test
  public void testFirebaseToken() {
    FirebaseToken firebaseToken = new FirebaseToken.Builder()
        .setUid("testUser")
        .setIssuer("test-project-id")
        .setEmail("test@example.com")
        .setEmailVerified(true)
        .setName("Test User")
        .setPicture("https://picture.url")
        .setClaims(ImmutableMap.<String, Object>of("custom", "claim"))
        .build();

    assertEquals("testUser", firebaseToken.getUid());
    assertEquals("test-project-id", firebaseToken.getIssuer());
    assertEquals("test@example.com", firebaseToken.getEmail());
    assertTrue(firebaseToken.isEmailVerified());
    assertEquals("Test User", firebaseToken.getName());
    assertEquals("https://picture.url", firebaseToken.getPicture());
    assertEquals("claim", firebaseToken.getClaims().get("custom"));
    assertEquals(1, firebaseToken.getClaims().size());
  }

  @Test
  public void testFirebaseTokenMinimal() {
    FirebaseToken firebaseToken = new FirebaseToken.Builder()
        .setUid("testUser")
        .build();

    assertEquals("testUser", firebaseToken.getUid());
    assertNull(firebaseToken.getIssuer());
    assertNull(firebaseToken.getEmail());
    assertFalse(firebaseToken.isEmailVerified());
    assertNull(firebaseToken.getName());
    assertNull(firebaseToken.getPicture());
    assertEquals(0, firebaseToken.getClaims().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseTokenNoUid() {
    new FirebaseToken.Builder().build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFirebaseTokenEmptyUid() {
    new FirebaseToken.Builder().setUid("").build();
  }
}
