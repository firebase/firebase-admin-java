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
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.auth.hash.Scrypt;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseAuthIT {

  private static final String ID_TOOLKIT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken";
  private static final String ID_TOOLKIT_URL2 =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword";
  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
  private static final HttpTransport transport = Utils.getDefaultTransport();

  private static FirebaseAuth auth;

  @BeforeClass
  public static void setUpClass() {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    auth = FirebaseAuth.getInstance(masterApp);
  }

  @Test
  public void testGetNonExistingUser() throws Exception {
    try {
      auth.getUserAsync("non.existing").get();
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testGetNonExistingUserByEmail() throws Exception {
    try {
      auth.getUserByEmailAsync("non.existing@definitely.non.existing").get();
      fail("No error thrown for non existing email");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testUpdateNonExistingUser() throws Exception {
    try {
      auth.updateUserAsync(new UpdateRequest("non.existing")).get();
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testDeleteNonExistingUser() throws Exception {
    try {
      auth.deleteUserAsync("non.existing").get();
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  private String randomPhoneNumber() {
    Random random = new Random();
    StringBuilder builder = new StringBuilder("+1");
    for (int i = 0; i < 10; i++) {
      builder.append(random.nextInt(10));
    }
    return builder.toString();
  }

  @Test
  public void testCreateUserWithParams() throws Exception {
    String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    String userEmail = ("test" + randomId.substring(0, 12) + "@example." + randomId.substring(12)
        + ".com").toLowerCase();
    String phone = randomPhoneNumber();
    CreateRequest user = new CreateRequest()
        .setUid(randomId)
        .setEmail(userEmail)
        .setPhoneNumber(phone)
        .setDisplayName("Random User")
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("password");

    UserRecord userRecord = auth.createUserAsync(user).get();
    try {
      assertEquals(randomId, userRecord.getUid());
      assertEquals("Random User", userRecord.getDisplayName());
      assertEquals(userEmail, userRecord.getEmail());
      assertEquals(phone, userRecord.getPhoneNumber());
      assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
      assertTrue(userRecord.isEmailVerified());
      assertFalse(userRecord.isDisabled());

      assertEquals(2, userRecord.getProviderData().length);
      List<String> providers = new ArrayList<>();
      for (UserInfo provider : userRecord.getProviderData()) {
        providers.add(provider.getProviderId());
      }
      assertTrue(providers.contains("password"));
      assertTrue(providers.contains("phone"));

      checkRecreate(randomId);
    } finally {
      auth.deleteUserAsync(userRecord.getUid()).get();
    }
  }

  @Test
  public void testUserLifecycle() throws Exception {
    // Create user
    UserRecord userRecord = auth.createUserAsync(new CreateRequest()).get();
    String uid = userRecord.getUid();

    // Get user
    userRecord = auth.getUserAsync(userRecord.getUid()).get();
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getDisplayName());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertFalse(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());
    assertTrue(userRecord.getUserMetadata().getCreationTimestamp() > 0);
    assertEquals(0, userRecord.getUserMetadata().getLastSignInTimestamp());
    assertEquals(0, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Update user
    String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    String userEmail = ("test" + randomId.substring(0, 12) + "@example." + randomId.substring(12)
        + ".com").toLowerCase();
    String phone = randomPhoneNumber();
    UpdateRequest request = userRecord.updateRequest()
        .setDisplayName("Updated Name")
        .setEmail(userEmail)
        .setPhoneNumber(phone)
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("secret");
    userRecord = auth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertEquals(phone, userRecord.getPhoneNumber());
    assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());
    assertEquals(2, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Get user by email
    userRecord = auth.getUserByEmailAsync(userRecord.getEmail()).get();
    assertEquals(uid, userRecord.getUid());

    // Disable user and remove properties
    request = userRecord.updateRequest()
        .setPhotoUrl(null)
        .setDisplayName(null)
        .setPhoneNumber(null)
        .setDisabled(true);
    userRecord = auth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());
    assertEquals(1, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Delete user
    auth.deleteUserAsync(userRecord.getUid()).get();
    try {
      auth.getUserAsync(userRecord.getUid()).get();
      fail("No error thrown for deleted user");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testListUsers() throws Exception {
    final List<String> uids = new ArrayList<>();

    try {
      uids.add(auth.createUserAsync(new CreateRequest().setPassword("password")).get().getUid());
      uids.add(auth.createUserAsync(new CreateRequest().setPassword("password")).get().getUid());
      uids.add(auth.createUserAsync(new CreateRequest().setPassword("password")).get().getUid());

      // Test list by batches
      final AtomicInteger collected = new AtomicInteger(0);
      ListUsersPage page = auth.listUsersAsync(null).get();
      while (page != null) {
        for (ExportedUserRecord user : page.getValues()) {
          if (uids.contains(user.getUid())) {
            collected.incrementAndGet();
            assertNotNull(user.getPasswordHash());
            assertNotNull(user.getPasswordSalt());
          }
        }
        page = page.getNextPage();
      }
      assertEquals(uids.size(), collected.get());

      // Test iterate all
      collected.set(0);
      page = auth.listUsersAsync(null).get();
      for (ExportedUserRecord user : page.iterateAll()) {
        if (uids.contains(user.getUid())) {
          collected.incrementAndGet();
          assertNotNull(user.getPasswordHash());
          assertNotNull(user.getPasswordSalt());
        }
      }
      assertEquals(uids.size(), collected.get());

      // Test iterate async
      collected.set(0);
      final Semaphore semaphore = new Semaphore(0);
      final AtomicReference<Throwable> error = new AtomicReference<>();
      ApiFuture<ListUsersPage> pageFuture = auth.listUsersAsync(null);
      ApiFutures.addCallback(pageFuture, new ApiFutureCallback<ListUsersPage>() {
        @Override
        public void onFailure(Throwable t) {
          error.set(t);
          semaphore.release();
        }

        @Override
        public void onSuccess(ListUsersPage result) {
          for (ExportedUserRecord user : result.iterateAll()) {
            if (uids.contains(user.getUid())) {
              collected.incrementAndGet();
              assertNotNull(user.getPasswordHash());
              assertNotNull(user.getPasswordSalt());
            }
          }
          semaphore.release();
        }
      });
      semaphore.acquire();
      assertEquals(uids.size(), collected.get());
      assertNull(error.get());
    } finally {
      for (String uid : uids) {
        auth.deleteUserAsync(uid).get();
      }
    }
  }

  @Test
  public void testCustomClaims() throws Exception {
    UserRecord userRecord = auth.createUserAsync(new CreateRequest()).get();
    String uid = userRecord.getUid();

    try {
      // New user should not have any claims
      assertTrue(userRecord.getCustomClaims().isEmpty());

      Map<String, Object> expected = ImmutableMap.<String, Object>of(
          "admin", true, "package", "gold");
      auth.setCustomUserClaimsAsync(uid, expected).get();

      // Should have 2 claims
      UserRecord updatedUser = auth.getUserAsync(uid).get();
      assertEquals(2, updatedUser.getCustomClaims().size());
      for (Map.Entry<String, Object> entry : expected.entrySet()) {
        assertEquals(entry.getValue(), updatedUser.getCustomClaims().get(entry.getKey()));
      }

      // User's ID token should have the custom claims
      String customToken = auth.createCustomTokenAsync(uid).get();
      String idToken = signInWithCustomToken(customToken);
      FirebaseToken decoded = auth.verifyIdTokenAsync(idToken).get();
      Map<String, Object> result = decoded.getClaims();
      for (Map.Entry<String, Object> entry : expected.entrySet()) {
        assertEquals(entry.getValue(), result.get(entry.getKey()));
      }

      // Should be able to remove custom claims
      auth.setCustomUserClaimsAsync(uid, null).get();
      updatedUser = auth.getUserAsync(uid).get();
      assertTrue(updatedUser.getCustomClaims().isEmpty());
    } finally {
      auth.deleteUserAsync(uid).get();
    }
  }

  @Test
  public void testCustomToken() throws Exception {
    String customToken = auth.createCustomTokenAsync("user1").get();
    String idToken = signInWithCustomToken(customToken);
    FirebaseToken decoded = auth.verifyIdTokenAsync(idToken).get();
    assertEquals("user1", decoded.getUid());
  }

  @Test
  public void testCustomTokenWithIAM() throws Exception {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(masterApp);
    AccessToken token = credentials.getAccessToken();
    if (token == null) {
      token = credentials.refreshAccessToken();
    }
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.of(token))
        .setServiceAccountId(((ServiceAccountSigner) credentials).getAccount())
        .setProjectId(IntegrationTestUtils.getProjectId())
        .build();
    FirebaseApp customApp = FirebaseApp.initializeApp(options, "tempApp");
    try {
      FirebaseAuth auth = FirebaseAuth.getInstance(customApp);
      String customToken = auth.createCustomTokenAsync("user1").get();
      String idToken = signInWithCustomToken(customToken);
      FirebaseToken decoded = auth.verifyIdTokenAsync(idToken).get();
      assertEquals("user1", decoded.getUid());
    } finally {
      customApp.delete();
    }
  }

  @Test
  public void testVerifyIdToken() throws Exception {
    String customToken = auth.createCustomTokenAsync("user2").get();
    String idToken = signInWithCustomToken(customToken);
    FirebaseToken decoded = auth.verifyIdTokenAsync(idToken).get();
    assertEquals("user2", decoded.getUid());
    decoded = auth.verifyIdTokenAsync(idToken, true).get();
    assertEquals("user2", decoded.getUid());
    Thread.sleep(1000);
    auth.revokeRefreshTokensAsync("user2").get();
    decoded = auth.verifyIdTokenAsync(idToken, false).get();
    assertEquals("user2", decoded.getUid());
    try {
      auth.verifyIdTokenAsync(idToken, true).get();
      fail("expecting exception");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.ID_TOKEN_REVOKED_ERROR,
                   ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
    idToken = signInWithCustomToken(customToken);
    decoded = auth.verifyIdTokenAsync(idToken, true).get();
    assertEquals("user2", decoded.getUid());    
    auth.deleteUserAsync("user2");
  }

  @Test
  public void testVerifySessionCookie() throws Exception {
    String customToken = auth.createCustomTokenAsync("user3").get();
    String idToken = signInWithCustomToken(customToken);

    SessionCookieOptions options = SessionCookieOptions.builder()
        .setExpiresIn(TimeUnit.HOURS.toMillis(1))
        .build();
    String sessionCookie = auth.createSessionCookieAsync(idToken, options).get();
    assertFalse(Strings.isNullOrEmpty(sessionCookie));

    FirebaseToken decoded = auth.verifySessionCookieAsync(sessionCookie).get();
    assertEquals("user3", decoded.getUid());
    decoded = auth.verifySessionCookieAsync(sessionCookie, true).get();
    assertEquals("user3", decoded.getUid());
    Thread.sleep(1000);

    auth.revokeRefreshTokensAsync("user3").get();
    decoded = auth.verifySessionCookieAsync(sessionCookie, false).get();
    assertEquals("user3", decoded.getUid());
    try {
      auth.verifySessionCookieAsync(sessionCookie, true).get();
      fail("expecting exception");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.SESSION_COOKIE_REVOKED_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }

    idToken = signInWithCustomToken(customToken);
    sessionCookie = auth.createSessionCookieAsync(idToken, options).get();
    decoded = auth.verifySessionCookieAsync(sessionCookie, true).get();
    assertEquals("user3", decoded.getUid());
    auth.deleteUserAsync("user3");
  }

  @Test
  public void testCustomTokenWithClaims() throws Exception {
    Map<String, Object> devClaims = ImmutableMap.<String, Object>of(
        "premium", true, "subscription", "silver");
    String customToken = auth.createCustomTokenAsync("user2", devClaims).get();
    String idToken = signInWithCustomToken(customToken);
    FirebaseToken decoded = auth.verifyIdTokenAsync(idToken).get();
    assertEquals("user2", decoded.getUid());
    assertTrue((Boolean) decoded.getClaims().get("premium"));
    assertEquals("silver", decoded.getClaims().get("subscription"));
  }

  @Test
  public void testImportUsers() throws Exception {
    final String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    final String userEmail = ("test" + randomId.substring(0, 12) + "@example."
        + randomId.substring(12) + ".com").toLowerCase();
    ImportUserRecord user = ImportUserRecord.builder()
        .setUid(randomId)
        .setEmail(userEmail)
        .build();

    UserImportResult result = auth.importUsersAsync(ImmutableList.of(user)).get();
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());

    try {
      UserRecord savedUser = auth.getUserAsync(randomId).get();
      assertEquals(userEmail, savedUser.getEmail());
    } finally {
      auth.deleteUserAsync(randomId).get();
    }
  }

  @Test
  public void testImportUsersWithPassword() throws Exception {
    final String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    final String userEmail = ("test" + randomId.substring(0, 12) + "@example."
        + randomId.substring(12) + ".com").toLowerCase();
    final byte[] passwordHash = BaseEncoding.base64().decode(
        "V358E8LdWJXAO7muq0CufVpEOXaj8aFiC7T/rcaGieN04q/ZPJ08WhJEHGjj9lz/2TT+/86N5VjVoc5DdBhBiw==");
    ImportUserRecord user = ImportUserRecord.builder()
        .setUid(randomId)
        .setEmail(userEmail)
        .setPasswordHash(passwordHash)
        .setPasswordSalt("NaCl".getBytes())
        .build();

    final byte[] scryptKey = BaseEncoding.base64().decode(
        "jxspr8Ki0RYycVU8zykbdLGjFQ3McFUH0uiiTvC8pVMXAn210wjLNmdZJzxUECKbm0QsEmYUSDzZvpjeJ9WmXA==");
    final byte[] saltSeparator = BaseEncoding.base64().decode("Bw==");
    UserImportResult result = auth.importUsersAsync(
        ImmutableList.of(user),
        UserImportOptions.withHash(Scrypt.builder()
            .setKey(scryptKey)
            .setSaltSeparator(saltSeparator)
            .setRounds(8)
            .setMemoryCost(14)
            .build())).get();
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());

    try {
      UserRecord savedUser = auth.getUserAsync(randomId).get();
      assertEquals(userEmail, savedUser.getEmail());
      String idToken = signInWithPassword(userEmail, "password");
      assertFalse(Strings.isNullOrEmpty(idToken));
    } finally {
      auth.deleteUserAsync(randomId).get();
    }
  }

  private String signInWithCustomToken(String customToken) throws IOException {
    GenericUrl url = new GenericUrl(ID_TOOLKIT_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    Map<String, Object> content = ImmutableMap.<String, Object>of(
        "token", customToken, "returnSecureToken", true);
    HttpRequest request = transport.createRequestFactory().buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content));
    request.setParser(new JsonObjectParser(jsonFactory));
    HttpResponse response = request.execute();
    try {
      GenericJson json = response.parseAs(GenericJson.class);
      return json.get("idToken").toString();
    } finally {
      response.disconnect();
    }
  }

  private String signInWithPassword(String email, String password) throws IOException {
    GenericUrl url = new GenericUrl(ID_TOOLKIT_URL2 + "?key="
        + IntegrationTestUtils.getApiKey());
    Map<String, Object> content = ImmutableMap.<String, Object>of(
        "email", email, "password", password);
    HttpRequest request = transport.createRequestFactory().buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content));
    request.setParser(new JsonObjectParser(jsonFactory));
    HttpResponse response = request.execute();
    try {
      GenericJson json = response.parseAs(GenericJson.class);
      return json.get("idToken").toString();
    } finally {
      response.disconnect();
    }
  }

  private void checkRecreate(String uid) throws Exception {
    try {
      auth.createUserAsync(new CreateRequest().setUid(uid)).get();
      fail("No error thrown for creating user with existing ID");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals("uid-already-exists", ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }
}
