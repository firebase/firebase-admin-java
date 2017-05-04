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

package com.google.firebase.auth.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.FixedClock;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.testing.TestUtils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FirebaseTokenFactoryTest {

  private static final JsonFactory FACTORY = new GsonFactory();
  private static final String USER_ID = "fuber";
  private static final GenericJson EXTRA_CLAIMS = new GenericJson();
  private static final String ISSUER = "test-484@mg-test-1210.iam.gserviceaccount.com";

  static {
    EXTRA_CLAIMS.set("one", 2).set("three", "four").setFactory(FACTORY);
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void checkSignatureForToken() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(512);
    KeyPair keys = keyGen.genKeyPair();

    FixedClock clock = new FixedClock(2002L);

    FirebaseTokenFactory tokenFactory = new FirebaseTokenFactory(FACTORY, clock);

    String jwt =
        tokenFactory.createSignedCustomAuthTokenForUser(
            USER_ID, EXTRA_CLAIMS, ISSUER, keys.getPrivate());

    FirebaseCustomAuthToken signedJwt = FirebaseCustomAuthToken.parse(FACTORY, jwt);
    assertEquals("RS256", signedJwt.getHeader().getAlgorithm());
    assertEquals(ISSUER, signedJwt.getPayload().getIssuer());
    assertEquals(ISSUER, signedJwt.getPayload().getSubject());
    assertEquals(USER_ID, signedJwt.getPayload().getUid());
    assertEquals(2L, signedJwt.getPayload().getIssuedAtTimeSeconds().longValue());

    assertTrue(TestUtils.verifySignature(signedJwt, ImmutableList.of(keys.getPublic())));
  }

  @Test
  public void failsWhenUidIsNull() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(512);
    KeyPair keys = keyGen.genKeyPair();

    FixedClock clock = new FixedClock(2002L);

    FirebaseTokenFactory tokenFactory = new FirebaseTokenFactory(FACTORY, clock);

    thrown.expect(IllegalStateException.class);
    tokenFactory.createSignedCustomAuthTokenForUser(null, ISSUER, keys.getPrivate());
  }

  @Test
  public void failsWhenUidIsTooLong() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(512);
    KeyPair keys = keyGen.genKeyPair();

    FixedClock clock = new FixedClock(2002L);

    FirebaseTokenFactory tokenFactory = new FirebaseTokenFactory(FACTORY, clock);

    thrown.expect(IllegalStateException.class);
    tokenFactory.createSignedCustomAuthTokenForUser(
        Strings.repeat("a", 129), ISSUER, keys.getPrivate());
  }

  @Test
  public void failsWhenIssuerIsNull() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(512);
    KeyPair keys = keyGen.genKeyPair();

    FixedClock clock = new FixedClock(2002L);

    FirebaseTokenFactory tokenFactory = new FirebaseTokenFactory(FACTORY, clock);

    thrown.expect(IllegalStateException.class);
    tokenFactory.createSignedCustomAuthTokenForUser(USER_ID, null, keys.getPrivate());
  }

  @Test
  public void failsWhenExtraClaimsContainsReservedKey() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(512);
    KeyPair keys = keyGen.genKeyPair();

    FixedClock clock = new FixedClock(2002L);

    FirebaseTokenFactory tokenFactory = new FirebaseTokenFactory(FACTORY, clock);

    Map<String, Object> extraClaims = ImmutableMap.<String, Object>of("iss", "repeat issuer");
    thrown.expect(IllegalArgumentException.class);
    tokenFactory.createSignedCustomAuthTokenForUser(
        USER_ID, extraClaims, ISSUER, keys.getPrivate());
  }
}
