package com.google.firebase.fpnv;

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



public class FirebasePnvTokenTest {
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

    FirebasePnvToken firebasePnvToken = new FirebasePnvToken(claims.getClaims());

    assertNotNull(firebasePnvToken);
    assertEquals(ImmutableList.of(), firebasePnvToken.getAudience());
  }

  @Test
  public void test_Audience_List() {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(subject)
        .audience(ImmutableList.of())
        .expirationTime(new Date(System.currentTimeMillis() + 10000))
        .build();

    FirebasePnvToken firebasePnvToken = new FirebasePnvToken(claims.getClaims());

    assertNotNull(firebasePnvToken);
    assertEquals(ImmutableList.of(), firebasePnvToken.getAudience());
  }

  @Test
  public void test_Audience_String() {
    Map<String, Object> claims = new HashMap<String, Object>();
    claims.put("sub", subject);
    claims.put("aud", ISSUER);


    FirebasePnvToken firebasePnvToken = new FirebasePnvToken(claims);

    assertNotNull(firebasePnvToken);
    assertEquals(ImmutableList.of(ISSUER), firebasePnvToken.getAudience());
  }

  @Test
  public void test_No_Sub() {
    Map<String, Object> claims = new HashMap<String, Object>();
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        new FirebasePnvToken(claims)
    );
    assertTrue(e.getMessage().contains("Claims map must at least contain sub"));
  }

  @Test
  public void test_Null_Sub() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        new FirebasePnvToken(null)
    );
    assertTrue(e.getMessage().contains("Claims map must at least contain sub"));
  }
}
