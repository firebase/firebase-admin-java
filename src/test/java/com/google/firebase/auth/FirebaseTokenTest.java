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
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.Test;

public class FirebaseTokenTest {

  @Test
  public void testFirebaseToken() {
    IdToken.Payload payload = new IdToken.Payload()
        .setSubject("testUser")
        .setIssuer("test-project-id")
        .set("email", "test@example.com")
        .set("email_verified", true)
        .set("name", "Test User")
        .set("picture", "https://picture.url")
        .set("custom", "claim");
    IdToken idToken = getIdToken(payload);

    FirebaseToken firebaseToken = new FirebaseToken(idToken);

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
    IdToken.Payload payload = new IdToken.Payload()
        .setSubject("testUser");
    IdToken idToken = getIdToken(payload);

    FirebaseToken firebaseToken = new FirebaseToken(idToken);

    assertEquals("testUser", firebaseToken.getUid());
    assertNull(firebaseToken.getIssuer());
    assertNull(firebaseToken.getEmail());
    assertFalse(firebaseToken.isEmailVerified());
    assertNull(firebaseToken.getName());
    assertNull(firebaseToken.getPicture());
    assertEquals(1, firebaseToken.getClaims().size());
  }

  private IdToken getIdToken(IdToken.Payload payload) {
    return new IdToken(
          new JsonWebSignature.Header(),
          payload,
          new byte[0], new byte[0]);
  }
}
