package com.google.firebase.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.DownloadAccountResponse;
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
    new UserRecord(null, JSON_FACTORY);
  }

  @Test
  public void testNoUid() throws IOException {
    String json = JSON_FACTORY.toString(ImmutableMap.of());
    try {
      parseUser(json);
      fail("No error thrown for response with no UID");
    } catch (IllegalArgumentException ignore) {
      // expected
    }
  }

  @Test
  public void testUserIdOnly() throws IOException {
    String json = JSON_FACTORY.toString(ImmutableMap.of("localId", "user"));
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertNull(userRecord.getDisplayName());
    assertEquals(0L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0L, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(0, userRecord.getCustomClaims().size());
    assertFalse(userRecord.isDisabled());
    assertFalse(userRecord.isEmailVerified());
    assertEquals(0, userRecord.getProviderData().length);
  }

  @Test
  public void testProviderInfo() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "providerUserInfo", ImmutableList.of(
            ImmutableMap.of("rawId", "provider"),
            ImmutableMap.of("rawId", "provider")
        )
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals(2, userRecord.getProviderData().length);
    for (UserInfo provider : userRecord.getProviderData()) {
      assertEquals("provider", provider.getUid());
      assertNull(provider.getDisplayName());
      assertNull(provider.getEmail());
      assertNull(provider.getPhoneNumber());
      assertNull(provider.getPhotoUrl());
      assertNull(provider.getProviderId());
    }
  }

  @Test
  public void testAllProviderInfo() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "providerUserInfo", ImmutableList.of(
            ImmutableMap.builder()
                .put("rawId", "provider")
                .put("displayName", "Display Name")
                .put("email", "email@provider.net")
                .put("phoneNumber", "1234567890")
                .put("photoUrl", "http://photo.url")
                .put("providerId", "providerId")
                .build()
            )
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals(1, userRecord.getProviderData().length);
    for (UserInfo provider : userRecord.getProviderData()) {
      assertEquals("provider", provider.getUid());
      assertEquals("Display Name", provider.getDisplayName());
      assertEquals("email@provider.net", provider.getEmail());
      assertEquals("1234567890", provider.getPhoneNumber());
      assertEquals("http://photo.url", provider.getPhotoUrl());
      assertEquals("providerId", provider.getProviderId());
    }
  }

  @Test
  public void testUserMetadata() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "createdAt", 10,
        "lastLoginAt", "20"
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals(10L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(20L, userRecord.getUserMetadata().getLastSignInTimestamp());
  }

  @Test
  public void testCustomClaims() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "customAttributes", "{\"foo\": \"bar\"}"
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals(1, userRecord.getCustomClaims().size());
    assertEquals("bar", userRecord.getCustomClaims().get("foo"));
  }

  @Test
  public void testEmptyCustomClaims() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "customAttributes", "{}"
    );
    String json = JSON_FACTORY.toString(resp);
    UserRecord userRecord = parseUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals(0, userRecord.getCustomClaims().size());
  }

  @Test
  public void testExportedUserUidOnly() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of("localId", "user");
    String json = JSON_FACTORY.toString(resp);
    ExportedUserRecord userRecord = parseExportedUser(json);
    assertEquals("user", userRecord.getUid());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertNull(userRecord.getDisplayName());
    assertEquals(0L, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0L, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(0, userRecord.getCustomClaims().size());
    assertFalse(userRecord.isDisabled());
    assertFalse(userRecord.isEmailVerified());
    assertEquals(0, userRecord.getProviderData().length);
    assertNull(userRecord.getPasswordHash());
    assertNull(userRecord.getPasswordSalt());
  }

  @Test
  public void testPasswordInfo() throws IOException {
    ImmutableMap<String, Object> resp = ImmutableMap.<String, Object>of(
        "localId", "user",
        "passwordHash", "secret",
        "salt", "pepper"
    );
    String json = JSON_FACTORY.toString(resp);
    ExportedUserRecord userRecord = parseExportedUser(json);
    assertEquals("user", userRecord.getUid());
    assertEquals("secret", userRecord.getPasswordHash());
    assertEquals("pepper", userRecord.getPasswordSalt());
  }

  private UserRecord parseUser(String json) throws IOException {
    InputStream stream = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
    GetAccountInfoResponse.User user = JSON_FACTORY.createJsonObjectParser()
        .parseAndClose(stream, Charset.defaultCharset(), GetAccountInfoResponse.User.class);
    return new UserRecord(user, JSON_FACTORY);
  }

  private ExportedUserRecord parseExportedUser(String json) throws IOException {
    InputStream stream = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
    DownloadAccountResponse.User user = JSON_FACTORY.createJsonObjectParser()
        .parseAndClose(stream, Charset.defaultCharset(), DownloadAccountResponse.User.class);
    return new ExportedUserRecord(user, JSON_FACTORY);
  }
}
