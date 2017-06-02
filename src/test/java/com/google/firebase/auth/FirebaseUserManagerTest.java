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

package com.google.firebase.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.firebase.testing.TestUtils;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class FirebaseUserManagerTest {

  private static final GsonFactory gson = new GsonFactory();

  @Test
  public void testGetUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    UserRecord userRecord = userManager.getUserById("testuser", "token");
    checkUserRecord(userRecord);
  }

  @Test
  public void testGetUserWithNotFoundError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUserError.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    try {
      userManager.getUserById("testuser", "token");
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException e) {
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR, e.getErrorCode());
    }
  }

  @Test
  public void testGetUserByEmail() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    UserRecord userRecord = userManager.getUserByEmail("testuser@example.com", "token");
    checkUserRecord(userRecord);
  }

  @Test
  public void testCreateUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("createUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    String uid = userManager.createUser(UserRecord.builder(), "token");
    assertEquals("testuser", uid);
  }

  @Test
  public void testUpdateUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("createUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    // should not throw
    userManager.updateUser(UserRecord.updater("testuser"), "token");
  }

  @Test
  public void testGetUserHttpError() throws Exception {
    for (int code : ImmutableList.of(302, 400, 401, 404, 500)) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
      response.setContent("{}");
      response.setStatusCode(code);
      MockHttpTransport transport = new MockHttpTransport.Builder()
          .setLowLevelHttpResponse(response)
          .build();
      FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
      try {
        userManager.getUserById("testuser", "token");
        fail("No error thrown for HTTP error");
      }  catch (FirebaseAuthException e) {
        assertTrue(e.getCause() instanceof IOException);
        assertEquals(FirebaseUserManager.INTERNAL_ERROR, e.getErrorCode());
      }
    }
  }

  @Test
  public void testGetUserJsonError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"not\" json}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    try {
      userManager.getUserById("testuser", "token");
      fail("No error thrown for JSON error");
    }  catch (FirebaseAuthException e) {
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  @Test
  public void testUserBuilder() {
    Map<String, Object> map = UserRecord.builder()
        .build();
    assertTrue(map.isEmpty());
  }

  @Test
  public void testUserBuilderWithParams() {
    Map<String, Object> map = UserRecord.builder()
        .setUid("TestUid")
        .setDisplayName("Display Name")
        .setPhotoUrl("http://test.com/example.png")
        .setEmail("test@example.com")
        .setEmailVerified(true)
        .setPassword("secret")
        .build();
    assertEquals(6, map.size());
    assertEquals("TestUid", map.get("localId"));
    assertEquals("Display Name", map.get("displayName"));
    assertEquals("http://test.com/example.png", map.get("photoUrl"));
    assertEquals("test@example.com", map.get("email"));
    assertTrue((Boolean) map.get("emailVerified"));
    assertEquals("secret", map.get("password"));
  }

  @Test
  public void testInvalidUid() {
    UserRecord.Builder builder = UserRecord.builder();
    try {
      builder.setUid(null);
      fail("No error thrown for null uid");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setUid("");
      fail("No error thrown for empty uid");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setUid(String.format("%0129d", 0));
      fail("No error thrown for long uid");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidDisplayName() {
    UserRecord.Builder builder = UserRecord.builder();
    try {
      builder.setDisplayName(null);
      fail("No error thrown for null display name");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPhotoUrl() {
    UserRecord.Builder builder = UserRecord.builder();
    try {
      builder.setPhotoUrl(null);
      fail("No error thrown for null photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setPhotoUrl("");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setPhotoUrl("not-a-url");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidEmail() {
    UserRecord.Builder builder = UserRecord.builder();
    try {
      builder.setEmail(null);
      fail("No error thrown for null email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setEmail("");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setEmail("not-an-email");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPassword() {
    UserRecord.Builder builder = UserRecord.builder();
    try {
      builder.setPassword(null);
      fail("No error thrown for null password");
    } catch (Exception ignore) {
      // expected
    }

    try {
      builder.setPassword("aaaaa");
      fail("No error thrown for short password");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testUserUpdater() {
    UserRecord.Updater updater = UserRecord.updater("test");
    Map<String, Object> map = updater
        .setDisplayName("Display Name")
        .setPhotoUrl("http://test.com/example.png")
        .setEmail("test@example.com")
        .setEmailVerified(true)
        .setPassword("secret")
        .update();
    assertEquals(6, map.size());
    assertEquals(updater.getUid(), map.get("localId"));
    assertEquals("Display Name", map.get("displayName"));
    assertEquals("http://test.com/example.png", map.get("photoUrl"));
    assertEquals("test@example.com", map.get("email"));
    assertTrue((Boolean) map.get("emailVerified"));
    assertEquals("secret", map.get("password"));
  }

  @Test
  public void testDeleteDisplayName() {
    Map<String, Object> map = UserRecord.updater("test")
        .setDisplayName(null)
        .update();
    assertEquals(ImmutableList.of("DISPLAY_NAME"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhotoUrl() {
    Map<String, Object> map = UserRecord.updater("test")
        .setPhotoUrl(null)
        .update();
    assertEquals(ImmutableList.of("PHOTO_URL"), map.get("deleteAttribute"));
  }

  @Test
  public void testInvalidUpdatePhotoUrl() {
    UserRecord.Updater updater = UserRecord.updater("test");
    try {
      updater.setPhotoUrl("");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      updater.setPhotoUrl("not-a-url");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdateEmail() {
    UserRecord.Updater updater = UserRecord.updater("test");
    try {
      updater.setEmail(null);
      fail("No error thrown for null email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      updater.setEmail("");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      updater.setEmail("not-an-email");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdatePassword() {
    UserRecord.Updater updater = UserRecord.updater("test");
    try {
      updater.setPassword(null);
      fail("No error thrown for null password");
    } catch (Exception ignore) {
      // expected
    }

    try {
      updater.setPassword("aaaaa");
      fail("No error thrown for short password");
    } catch (Exception ignore) {
      // expected
    }
  }

  private void checkUserRecord(UserRecord userRecord) {
    assertEquals("testuser", userRecord.getUid());
    assertEquals("testuser@example.com", userRecord.getEmail());
    assertEquals("Test User", userRecord.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", userRecord.getPhotoUrl());
    assertEquals(1234567890, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(1, userRecord.getProviderData().length);
    assertFalse(userRecord.isDisabled());
    assertTrue(userRecord.isEmailVerified());

    UserInfo provider = userRecord.getProviderData()[0];
    assertEquals("testuser@example.com", provider.getUid());
    assertEquals("testuser@example.com", provider.getEmail());
    assertEquals("Test User", provider.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", provider.getPhotoUrl());
    assertEquals("password", provider.getProviderId());
  }

}
