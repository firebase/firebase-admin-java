package com.google.firebase.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.junit.Test;

public class UserRecordTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  @Test(expected = NullPointerException.class)
  public void testNullResponse() {
    new UserRecord(null);
  }

  @Test
  public void testNoUid() throws IOException {
    String json = JSON_FACTORY.toString(ImmutableMap.of());
    try {
      parse(json);
      fail("No error thrown for response with no UID");
    } catch (IllegalArgumentException ignore) {
      // expected
    }
  }

  @Test
  public void testUserIdOnly() throws IOException {
    String json = JSON_FACTORY.toString(ImmutableMap.of("localId", "user"));
    UserRecord userRecord = parse(json);
    assertEquals("user", userRecord.getUid());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertNull(userRecord.getDisplayName());
    assertEquals(0L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0L, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertNull(userRecord.getCustomClaims());
    assertFalse(userRecord.isDisabled());
    assertFalse(userRecord.isEmailVerified());
    assertEquals(0, userRecord.getProviderData().length);
  }

  @Test
  public void testUserMetadata() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "createdAt", 10,
        "lastLoginAt", "20"
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parse(json);
    assertEquals("user", userRecord.getUid());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertNull(userRecord.getDisplayName());
    assertEquals(10L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(20L, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertNull(userRecord.getCustomClaims());
    assertFalse(userRecord.isDisabled());
    assertFalse(userRecord.isEmailVerified());
    assertEquals(0, userRecord.getProviderData().length);
  }

  @Test
  public void testCustomClaims() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "customAttributes", "{\"foo\": \"bar\"}"
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parse(json);
    assertEquals("user", userRecord.getUid());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertNull(userRecord.getDisplayName());
    assertEquals(0L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0L, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(1, userRecord.getCustomClaims().size());
    assertEquals("bar", userRecord.getCustomClaims().get("foo"));
    assertFalse(userRecord.isDisabled());
    assertFalse(userRecord.isEmailVerified());
    assertEquals(0, userRecord.getProviderData().length);
  }

  private UserRecord parse(String json) throws IOException {
    InputStream stream = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
    GetAccountInfoResponse.User user = JSON_FACTORY.createJsonObjectParser()
        .parseAndClose(stream, Charset.defaultCharset(), GetAccountInfoResponse.User.class);
    return new UserRecord(user);
  }
}
