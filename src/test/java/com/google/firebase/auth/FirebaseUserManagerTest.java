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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class FirebaseUserManagerTest {

  private static final String TEST_TOKEN = "token";
  private static final GoogleCredentials credentials = new MockGoogleCredentials(TEST_TOKEN);

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetUser() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("getUser.json"));
    UserRecord userRecord = FirebaseAuth.getInstance().getUserAsync("testuser").get();
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserWithNotFoundError() throws Exception {
    initializeAppForUserManagement(TestUtils.loadResource("getUserError.json"));
    try {
      FirebaseAuth.getInstance().getUserAsync("testuser").get();
      fail("No error thrown for invalid response");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR, authException.getErrorCode());
    }
  }

  @Test
  public void testGetUserByEmail() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("getUser.json"));
    UserRecord userRecord = FirebaseAuth.getInstance()
        .getUserByEmailAsync("testuser@example.com").get();
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserByEmailWithNotFoundError() throws Exception {
    initializeAppForUserManagement(TestUtils.loadResource("getUserError.json"));
    try {
      FirebaseAuth.getInstance().getUserByEmailAsync("testuser@example.com").get();
      fail("No error thrown for invalid response");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR, authException.getErrorCode());
    }
  }

  @Test
  public void testGetUserByPhoneNumber() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("getUser.json"));
    UserRecord userRecord = FirebaseAuth.getInstance()
        .getUserByPhoneNumberAsync("+1234567890").get();
    checkUserRecord(userRecord);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserByPhoneNumberWithNotFoundError() throws Exception {
    initializeAppForUserManagement(TestUtils.loadResource("getUserError.json"));
    try {
      FirebaseAuth.getInstance().getUserByPhoneNumberAsync("+1234567890").get();
      fail("No error thrown for invalid response");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR, authException.getErrorCode());
    }
  }

  @Test
  public void testListUsers() throws Exception {
    final TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("listUsers.json"));
    ListUsersPage page = FirebaseAuth.getInstance().listUsersAsync(null, 999).get();
    assertEquals(2, Iterables.size(page.getValues()));
    for (ExportedUserRecord userRecord : page.getValues()) {
      checkUserRecord(userRecord);
      assertEquals("passwordHash", userRecord.getPasswordHash());
      assertEquals("passwordSalt", userRecord.getPasswordSalt());
    }
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    GenericJson parsed = jsonFactory.fromString(new String(out.toByteArray()), GenericJson.class);
    assertEquals(new BigDecimal(999), parsed.get("maxResults"));
    assertNull(parsed.get("nextPageToken"));
  }

  @Test
  public void testListUsersWithPageToken() throws Exception {
    final TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("listUsers.json"));
    ListUsersPage page = FirebaseAuth.getInstance().listUsersAsync("token", 999).get();
    assertEquals(2, Iterables.size(page.getValues()));
    for (ExportedUserRecord userRecord : page.getValues()) {
      checkUserRecord(userRecord);
      assertEquals("passwordHash", userRecord.getPasswordHash());
      assertEquals("passwordSalt", userRecord.getPasswordSalt());
    }
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    GenericJson parsed = jsonFactory.fromString(new String(out.toByteArray()), GenericJson.class);
    assertEquals(new BigDecimal(999), parsed.get("maxResults"));
    assertEquals("token", parsed.get("nextPageToken"));
  }

  @Test
  public void testListZeroUsers() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement("{}");
    ListUsersPage page = FirebaseAuth.getInstance().listUsersAsync(null).get();
    assertTrue(Iterables.isEmpty(page.getValues()));
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testCreateUser() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("createUser.json"),
        TestUtils.loadResource("getUser.json"));
    UserRecord user = FirebaseAuth.getInstance().createUserAsync(new CreateRequest()).get();
    checkUserRecord(user);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testUpdateUser() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("createUser.json"),
        TestUtils.loadResource("getUser.json"));
    UserRecord user = FirebaseAuth.getInstance()
        .updateUserAsync(new UpdateRequest("testuser")).get();
    checkUserRecord(user);
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testSetCustomAttributes() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("createUser.json"));
    // should not throw
    ImmutableMap<String, Object> claims = ImmutableMap.<String, Object>of(
        "admin", true, "package", "gold");
    FirebaseAuth.getInstance().setCustomUserClaimsAsync("testuser", claims).get();
    checkRequestHeaders(interceptor);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    GenericJson parsed = jsonFactory.fromString(new String(out.toByteArray()), GenericJson.class);
    assertEquals("testuser", parsed.get("localId"));
    assertEquals(jsonFactory.toString(claims), parsed.get("customAttributes"));
  }

  @Test
  public void testRevokeRefreshTokens() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("createUser.json"));
    // should not throw
    FirebaseAuth.getInstance().revokeRefreshTokensAsync("testuser").get();
    checkRequestHeaders(interceptor);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    GenericJson parsed = jsonFactory.fromString(new String(out.toByteArray()), GenericJson.class);
    assertEquals("testuser", parsed.get("localId"));
    assertNotNull(parsed.get("validSince"));
  }

  @Test
  public void testDeleteUser() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForUserManagement(
        TestUtils.loadResource("deleteUser.json"));
    // should not throw
    FirebaseAuth.getInstance().deleteUserAsync("testuser").get();
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testGetUserHttpError() throws Exception {
    List<UserManagerOp> operations = ImmutableList.<UserManagerOp>builder()
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.getUserAsync("testuser").get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.getUserByEmailAsync("testuser@example.com").get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.getUserByPhoneNumberAsync("+1234567890").get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.createUserAsync(new CreateRequest()).get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.updateUserAsync(new UpdateRequest("test")).get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.deleteUserAsync("testuser").get();
          }
        })
        .add(new UserManagerOp() {
          @Override
          public void call(FirebaseAuth auth) throws Exception {
            auth.listUsersAsync(null, 1000).get();
          }
        })
        .build();

    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setHttpTransport(transport)
        .build());

    // Test for common HTTP error codes
    for (int code : ImmutableList.of(302, 400, 401, 404, 500)) {
      for (UserManagerOp operation : operations) {
        // Need to reset these every iteration
        response.setContent("{}");
        response.setStatusCode(code);
        try {
          operation.call(FirebaseAuth.getInstance());
          fail("No error thrown for HTTP error: " + code);
        } catch (ExecutionException e) {
          assertTrue(e.getCause() instanceof FirebaseAuthException);
          FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
          String msg = String.format("Unexpected HTTP response with status: %d; body: {}", code);
          assertEquals(msg, authException.getMessage());
          assertTrue(authException.getCause() instanceof HttpResponseException);
          assertEquals(FirebaseUserManager.INTERNAL_ERROR, authException.getErrorCode());
        }
      }
    }

    // Test error payload parsing
    for (UserManagerOp operation : operations) {
      response.setContent("{\"error\": {\"message\": \"USER_NOT_FOUND\"}}");
      response.setStatusCode(500);
      try {
        operation.call(FirebaseAuth.getInstance());
        fail("No error thrown for HTTP error");
      }  catch (ExecutionException e) {
        assertTrue(e.getCause().toString(), e.getCause() instanceof FirebaseAuthException);
        FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
        assertEquals("User management service responded with an error", authException.getMessage());
        assertTrue(authException.getCause() instanceof HttpResponseException);
        assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR, authException.getErrorCode());
      }
    }
  }

  @Test
  public void testGetUserMalformedJsonError() throws Exception {
    initializeAppForUserManagement("{\"not\" json}");
    try {
      FirebaseAuth.getInstance().getUserAsync("testuser").get();
      fail("No error thrown for JSON error");
    }  catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertTrue(authException.getCause() instanceof IOException);
      assertEquals(FirebaseUserManager.INTERNAL_ERROR, authException.getErrorCode());
    }
  }

  @Test
  public void testGetUserUnexpectedHttpError() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"not\" json}");
    response.setStatusCode(500);
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setHttpTransport(transport)
        .build());
    try {
      FirebaseAuth.getInstance().getUserAsync("testuser").get();
      fail("No error thrown for JSON error");
    }  catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertTrue(authException.getCause() instanceof HttpResponseException);
      assertEquals("Unexpected HTTP response with status: 500; body: {\"not\" json}",
          authException.getMessage());
      assertEquals(FirebaseUserManager.INTERNAL_ERROR, authException.getErrorCode());
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
  public void testUserUpdater() throws IOException {
    UpdateRequest update = new UpdateRequest("test");
    Map<String, Object> claims = ImmutableMap.<String, Object>of("admin", true, "package", "gold");
    Map<String, Object> map = update
        .setDisplayName("Display Name")
        .setPhotoUrl("http://test.com/example.png")
        .setEmail("test@example.com")
        .setPhoneNumber("+1234567890")
        .setEmailVerified(true)
        .setPassword("secret")
        .setCustomClaims(claims)
        .getProperties(Utils.getDefaultJsonFactory());
    assertEquals(8, map.size());
    assertEquals(update.getUid(), map.get("localId"));
    assertEquals("Display Name", map.get("displayName"));
    assertEquals("http://test.com/example.png", map.get("photoUrl"));
    assertEquals("test@example.com", map.get("email"));
    assertEquals("+1234567890", map.get("phoneNumber"));
    assertTrue((Boolean) map.get("emailVerified"));
    assertEquals("secret", map.get("password"));
    assertEquals(Utils.getDefaultJsonFactory().toString(claims), map.get("customAttributes"));
  }

  @Test
  public void testNullJsonFactory() {
    UpdateRequest update = new UpdateRequest("test");
    Map<String, Object> claims = ImmutableMap.<String, Object>of("admin", true, "package", "gold");
    update.setCustomClaims(claims);
    try {
      update.getProperties(null);
      fail("No error thrown for null JsonFactory");
    } catch (NullPointerException ignore) {
      // expected
    }
  }

  @Test
  public void testNullCustomClaims() {
    UpdateRequest update = new UpdateRequest("test");
    Map<String, Object> map = update
        .setCustomClaims(null)
        .getProperties(Utils.getDefaultJsonFactory());
    assertEquals(2, map.size());
    assertEquals(update.getUid(), map.get("localId"));
    assertEquals("{}", map.get("customAttributes"));
  }

  @Test
  public void testEmptyCustomClaims() {
    UpdateRequest update = new UpdateRequest("test");
    Map<String, Object> map = update
        .setCustomClaims(ImmutableMap.<String, Object>of())
        .getProperties(Utils.getDefaultJsonFactory());
    assertEquals(2, map.size());
    assertEquals(update.getUid(), map.get("localId"));
    assertEquals("{}", map.get("customAttributes"));
  }

  @Test
  public void testDeleteDisplayName() {
    Map<String, Object> map = new UpdateRequest("test")
        .setDisplayName(null)
        .getProperties(Utils.getDefaultJsonFactory());
    assertEquals(ImmutableList.of("DISPLAY_NAME"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhotoUrl() {
    Map<String, Object> map = new UpdateRequest("test")
        .setPhotoUrl(null)
        .getProperties(Utils.getDefaultJsonFactory());
    assertEquals(ImmutableList.of("PHOTO_URL"), map.get("deleteAttribute"));
  }

  @Test
  public void testDeletePhoneNumber() {
    Map<String, Object> map = new UpdateRequest("test")
        .setPhoneNumber(null)
        .getProperties(Utils.getDefaultJsonFactory());
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

  @Test
  public void testInvalidCustomClaims() {
    UpdateRequest update = new UpdateRequest("test");
    for (String claim : FirebaseUserManager.RESERVED_CLAIMS) {
      try {
        update.setCustomClaims(ImmutableMap.<String, Object>of(claim, "value"));
        fail("No error thrown for reserved claim");
      } catch (Exception ignore) {
        // expected
      }
    }
  }

  @Test
  public void testLargeCustomClaims() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 1001; i++) {
      builder.append("a");
    }
    UpdateRequest update = new UpdateRequest("test");
    update.setCustomClaims(ImmutableMap.<String, Object>of("key", builder.toString()));
    try {
      update.getProperties(Utils.getDefaultJsonFactory());
      fail("No error thrown for large claims payload");
    } catch (Exception ignore) {
      // expected
    }
  }

  private static TestResponseInterceptor initializeAppForUserManagement(String ...responses) {
    List<MockLowLevelHttpResponse> mocks = new ArrayList<>();
    for (String response : responses) {
      mocks.add(new MockLowLevelHttpResponse().setContent(response));
    }
    MockHttpTransport transport = new MultiRequestMockHttpTransport(mocks);
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setHttpTransport(transport)
        .build());
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseUserManager userManager = auth.getUserManager();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    userManager.setInterceptor(interceptor);
    return interceptor;
  }

  private static void checkUserRecord(UserRecord userRecord) {
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
    assertEquals(1494364393000L, userRecord.getTokensValidAfterTimestamp());

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

    Map<String, Object> claims = userRecord.getCustomClaims();
    assertEquals(2, claims.size());
    assertTrue((boolean) claims.get("admin"));
    assertEquals("gold", claims.get("package"));
  }

  private static void checkRequestHeaders(TestResponseInterceptor interceptor) {
    HttpHeaders headers = interceptor.getResponse().getRequest().getHeaders();
    String auth = "Bearer " + TEST_TOKEN;
    assertEquals(auth, headers.getFirstHeaderStringValue("Authorization"));

    String clientVersion = "Java/Admin/" + SdkUtils.getVersion();
    assertEquals(clientVersion, headers.getFirstHeaderStringValue("X-Client-Version"));
  }

  private interface UserManagerOp {
    void call(FirebaseAuth auth) throws Exception;
  }
  
}
