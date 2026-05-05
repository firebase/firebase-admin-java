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

package com.google.firebase.phonenumberverification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class FirebasePhoneNumberVerificationTokenTest {
  private static final String PROJECT_ID = "mock-project-id-1";
  private static final String ISSUER = "https://fpnv.googleapis.com/projects/" + PROJECT_ID;
  private final String subject = "+15551234567";

  @After
  public void tearDown() {
    FirebaseProcessEnvironment.clearCache();
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void test_Audience_Empty() {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(subject)
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    FirebasePhoneNumberVerificationToken token = new FirebasePhoneNumberVerificationToken(claims.getClaims());

    assertNotNull(token);
    assertEquals(ImmutableList.of(), token.getAudience());
  }

  @Test
  public void test_Audience_List() {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(subject)
        .audience(ImmutableList.of())
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    FirebasePhoneNumberVerificationToken token = new FirebasePhoneNumberVerificationToken(claims.getClaims());

    assertNotNull(token);
    assertEquals(ImmutableList.of(), token.getAudience());
  }

  @Test
  public void test_Audience_String() {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", subject);
    claims.put("aud", ISSUER);

    FirebasePhoneNumberVerificationToken token = new FirebasePhoneNumberVerificationToken(claims);

    assertNotNull(token);
    assertEquals(ImmutableList.of(ISSUER), token.getAudience());
  }

  @Test
  public void test_No_Sub() {
    Map<String, Object> claims = new HashMap<>();
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        new FirebasePhoneNumberVerificationToken(claims)
    );
    assertTrue(e.getMessage().contains("Claims map must at least contain sub"));
  }

  @Test
  public void test_Null_Sub() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        new FirebasePhoneNumberVerificationToken(null)
    );
    assertTrue(e.getMessage().contains("Claims map must at least contain sub"));
  }
}
