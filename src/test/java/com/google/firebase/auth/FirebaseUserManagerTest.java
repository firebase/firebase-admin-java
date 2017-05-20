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
    User user = userManager.getUserById("testuser", "token");
    checkUser(user);
  }

  @Test
  public void testGetUserError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUserError.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    try {
      userManager.getUserById("testuser", "token");
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException ignore) {
      // expected
    }
  }

  @Test
  public void testGetUserErrorInvalidJson() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("foobar");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    try {
      userManager.getUserById("testuser", "token");
      fail("No error thrown for invalid response");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testGetUserErrorInvalidHttpCode() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setStatusCode(500);
    response.setContent("error message");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    try {
      userManager.getUserById("testuser", "token");
      fail("No error thrown for invalid response");
    } catch (IOException ignore) {
      // expected
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
    User user = userManager.getUserByEmail("testuser@example.com", "token");
    checkUser(user);
  }

  @Test
  public void testCreateUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("createUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport);
    String uid = userManager.createUser(User.builder(), "token");
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
    userManager.updateUser(User.updater("testuser"), "token");
  }

  @Test
  public void testUserBuilder() {
    Map<String, Object> map = User.builder()
        .build();
    assertTrue(map.isEmpty());
  }

  @Test
  public void testUserBuilderWithParams() {
    Map<String, Object> map = User.builder()
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
    User.Builder builder = User.builder();
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
    User.Builder builder = User.builder();
    try {
      builder.setDisplayName(null);
      fail("No error thrown for null display name");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPhotoUrl() {
    User.Builder builder = User.builder();
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
    User.Builder builder = User.builder();
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
    User.Builder builder = User.builder();
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
    User.Updater updater = User.updater("test");
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
    Map<String, Object> map = User.updater("test")
        .setDisplayName(null)
        .update();
    assertEquals(ImmutableList.of("DISPLAY_NAME"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhotoUrl() {
    Map<String, Object> map = User.updater("test")
        .setPhotoUrl(null)
        .update();
    assertEquals(ImmutableList.of("PHOTO_URL"), map.get("deleteAttribute"));
  }

  @Test
  public void testInvalidUpdateUser() {
    User user = new User();
    try {
      user.updater();
      fail("No error thrown for invalid user update");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdatePhotoUrl() {
    User.Updater updater = User.updater("test");
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
    User.Updater updater = User.updater("test");
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
    User.Updater updater = User.updater("test");
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

  private void checkUser(User user) {
    assertEquals("testuser", user.getUid());
    assertEquals("testuser@example.com", user.getEmail());
    assertEquals("Test User", user.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", user.getPhotoUrl());
    assertEquals(1234567890, user.getCreatedAt());
    assertEquals(1, user.getProviders().length);
    assertFalse(user.isDisabled());
    assertEquals(0, user.getLastLoginAt());
    assertTrue(user.isEmailVerified());

    Provider provider = user.getProviders()[0];
    assertEquals("testuser@example.com", provider.getUid());
    assertEquals("testuser@example.com", provider.getEmail());
    assertEquals("Test User", provider.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", provider.getPhotoUrl());
    assertEquals("password", provider.getProviderId());
  }

}
