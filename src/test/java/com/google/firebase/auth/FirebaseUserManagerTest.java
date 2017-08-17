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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.testing.TestUtils;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class FirebaseUserManagerTest {

  private static final String TEST_TOKEN = "token";
  private static final GsonFactory gson = new GsonFactory();
  private static final GoogleCredentials credentials = new MockGoogleCredentials(TEST_TOKEN);

  @Test
  public void testGetUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    UserRecord userRecord = userManager.getUserById("testuser");
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserWithNotFoundError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUserError.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    try {
      userManager.getUserById("testuser");
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
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    UserRecord userRecord = userManager.getUserByEmail("testuser@example.com");
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserByPhoneNumber() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("getUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    UserRecord userRecord = userManager.getUserByPhoneNumber("+1234567890");
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testCreateUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("createUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    String uid = userManager.createUser(new CreateRequest());
    assertEquals("testuser", uid);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testUpdateUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("createUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    // should not throw
    userManager.updateUser(new UpdateRequest("testuser"));
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testDeleteUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("deleteUser.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    // should not throw
    userManager.deleteUser("testuser");
    checkRequestHeaders(interceptor);
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
      FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
      try {
        userManager.getUserById("testuser");
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
    FirebaseUserManager userManager = new FirebaseUserManager(gson, transport, credentials);
    try {
      userManager.getUserById("testuser");
      fail("No error thrown for JSON error");
    }  catch (FirebaseAuthException e) {
      assertTrue(e.getCause() instanceof IOException);
    }
  }

  @Test
  public void testUserBuilder() {
    Map<String, Object> map = new CreateRequest().getProperties();
    assertTrue(map.isEmpty());
  }

  @Test
  public void testUserBuilderWithParams() {
    Map<String, Object> map = new CreateRequest()
        .setUid("TestUid")
        .setDisplayName("Display Name")
        .setPhotoUrl("http://test.com/example.png")
        .setEmail("test@example.com")
        .setPhoneNumber("+1234567890")
        .setEmailVerified(true)
        .setPassword("secret")
        .getProperties();
    assertEquals(7, map.size());
    assertEquals("TestUid", map.get("localId"));
    assertEquals("Display Name", map.get("displayName"));
    assertEquals("http://test.com/example.png", map.get("photoUrl"));
    assertEquals("test@example.com", map.get("email"));
    assertEquals("+1234567890", map.get("phoneNumber"));
    assertTrue((Boolean) map.get("emailVerified"));
    assertEquals("secret", map.get("password"));
  }

  @Test
  public void testInvalidUid() {
    CreateRequest user = new CreateRequest();
    try {
      user.setUid(null);
      fail("No error thrown for null uid");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setUid("");
      fail("No error thrown for empty uid");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setUid(String.format("%0129d", 0));
      fail("No error thrown for long uid");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidDisplayName() {
    CreateRequest user = new CreateRequest();
    try {
      user.setDisplayName(null);
      fail("No error thrown for null display name");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPhotoUrl() {
    CreateRequest user = new CreateRequest();
    try {
      user.setPhotoUrl(null);
      fail("No error thrown for null photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setPhotoUrl("");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setPhotoUrl("not-a-url");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidEmail() {
    CreateRequest user = new CreateRequest();
    try {
      user.setEmail(null);
      fail("No error thrown for null email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setEmail("");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setEmail("not-an-email");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPhoneNumber() {
    CreateRequest user = new CreateRequest();
    try {
      user.setPhoneNumber(null);
      fail("No error thrown for null phone number");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setPhoneNumber("");
      fail("No error thrown for invalid phone number");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setPhoneNumber("not-a-phone");
      fail("No error thrown for invalid phone number");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidPassword() {
    CreateRequest user = new CreateRequest();
    try {
      user.setPassword(null);
      fail("No error thrown for null password");
    } catch (Exception ignore) {
      // expected
    }

    try {
      user.setPassword("aaaaa");
      fail("No error thrown for short password");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testUserUpdater() {
    UpdateRequest update = new UpdateRequest("test");
    Map<String, Object> map = update
        .setDisplayName("Display Name")
        .setPhotoUrl("http://test.com/example.png")
        .setEmail("test@example.com")
        .setPhoneNumber("+1234567890")
        .setEmailVerified(true)
        .setPassword("secret")
        .getProperties();
    assertEquals(7, map.size());
    assertEquals(update.getUid(), map.get("localId"));
    assertEquals("Display Name", map.get("displayName"));
    assertEquals("http://test.com/example.png", map.get("photoUrl"));
    assertEquals("test@example.com", map.get("email"));
    assertEquals("+1234567890", map.get("phoneNumber"));
    assertTrue((Boolean) map.get("emailVerified"));
    assertEquals("secret", map.get("password"));
  }

  @Test
  public void testDeleteDisplayName() {
    Map<String, Object> map = new UpdateRequest("test")
        .setDisplayName(null)
        .getProperties();
    assertEquals(ImmutableList.of("DISPLAY_NAME"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhotoUrl() {
    Map<String, Object> map = new UpdateRequest("test")
        .setPhotoUrl(null)
        .getProperties();
    assertEquals(ImmutableList.of("PHOTO_URL"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhoneNumber() {
    Map<String, Object> map = new UpdateRequest("test")
        .setPhoneNumber(null)
        .getProperties();
    assertEquals(ImmutableList.of("phone"), map.get("deleteProvider"));
  }

  @Test
  public void testInvalidUpdatePhotoUrl() {
    UpdateRequest update = new UpdateRequest("test");
    try {
      update.setPhotoUrl("");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }

    try {
      update.setPhotoUrl("not-a-url");
      fail("No error thrown for invalid photo url");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdateEmail() {
    UpdateRequest update = new UpdateRequest("test");
    try {
      update.setEmail(null);
      fail("No error thrown for null email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      update.setEmail("");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }

    try {
      update.setEmail("not-an-email");
      fail("No error thrown for invalid email");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdatePhoneNumber() {
    UpdateRequest update = new UpdateRequest("test");

    try {
      update.setPhoneNumber("");
      fail("No error thrown for invalid phone number");
    } catch (Exception ignore) {
      // expected
    }

    try {
      update.setPhoneNumber("not-a-phone");
      fail("No error thrown for invalid phone number");
    } catch (Exception ignore) {
      // expected
    }
  }

  @Test
  public void testInvalidUpdatePassword() {
    UpdateRequest update = new UpdateRequest("test");
    try {
      update.setPassword(null);
      fail("No error thrown for null password");
    } catch (Exception ignore) {
      // expected
    }

    try {
      update.setPassword("aaaaa");
      fail("No error thrown for short password");
    } catch (Exception ignore) {
      // expected
    }
  }

  private void checkUserRecord(UserRecord userRecord) {
    assertEquals("testuser", userRecord.getUid());
    assertEquals("testuser@example.com", userRecord.getEmail());
    assertEquals("+1234567890", userRecord.getPhoneNumber());
    assertEquals("Test User", userRecord.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", userRecord.getPhotoUrl());
    assertEquals(1234567890, userRecord.getUserMetadata().getCreationTimestamp());
    assertEquals(0, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(2, userRecord.getProviderData().length);
    assertFalse(userRecord.isDisabled());
    assertTrue(userRecord.isEmailVerified());

    UserInfo provider = userRecord.getProviderData()[0];
    assertEquals("testuser@example.com", provider.getUid());
    assertEquals("testuser@example.com", provider.getEmail());
    assertNull(provider.getPhoneNumber());
    assertEquals("Test User", provider.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", provider.getPhotoUrl());
    assertEquals("password", provider.getProviderId());

    provider = userRecord.getProviderData()[1];
    assertEquals("+1234567890", provider.getUid());
    assertNull(provider.getEmail());
    assertEquals("+1234567890", provider.getPhoneNumber());
    assertEquals("phone", provider.getProviderId());
  }

  private void checkRequestHeaders(TestResponseInterceptor interceptor) {
    HttpHeaders headers = interceptor.response.getRequest().getHeaders();
    String auth = "Bearer " + TEST_TOKEN;
    assertEquals(auth, headers.getFirstHeaderStringValue("Authorization"));

    String clientVersion = "Java/Admin/" + SdkUtils.getVersion();
    assertEquals(clientVersion, headers.getFirstHeaderStringValue("X-Client-Version"));
  }

  private static class TestResponseInterceptor implements HttpResponseInterceptor {

    private HttpResponse response;

    @Override
    public void interceptResponse(HttpResponse response) throws IOException {
      this.response = response;
    }
  }

}
