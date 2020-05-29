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
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.ProviderConfigTestUtils.TemporaryProviderConfig;
import com.google.firebase.auth.hash.Scrypt;
import com.google.firebase.internal.Nullable;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class FirebaseAuthIT {

  private static final String VERIFY_CUSTOM_TOKEN_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken";
  private static final String VERIFY_PASSWORD_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword";
  private static final String RESET_PASSWORD_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/resetPassword";
  private static final String EMAIL_LINK_SIGN_IN_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/emailLinkSignin";
  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
  private static final HttpTransport transport = Utils.getDefaultTransport();
  private static final String ACTION_LINK_CONTINUE_URL = "http://localhost/?a=1&b=2#c=3";

  private static final FirebaseAuth auth = FirebaseAuth.getInstance(
      IntegrationTestUtils.ensureDefaultApp());

  @Rule public final TemporaryUser temporaryUser = new TemporaryUser();
  @Rule public final TemporaryProviderConfig temporaryProviderConfig =
      new TemporaryProviderConfig(auth);

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
      auth.updateUserAsync(new UserRecord.UpdateRequest("non.existing")).get();
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

  @Test
  public void testDeleteUsers() throws Exception {
    UserRecord user1 = newUserWithParams();
    UserRecord user2 = newUserWithParams();
    UserRecord user3 = newUserWithParams();

    DeleteUsersResult deleteUsersResult =
        slowDeleteUsersAsync(ImmutableList.of(user1.getUid(), user2.getUid(), user3.getUid()))
            .get();

    assertEquals(3, deleteUsersResult.getSuccessCount());
    assertEquals(0, deleteUsersResult.getFailureCount());
    assertTrue(deleteUsersResult.getErrors().isEmpty());

    GetUsersResult getUsersResult =
        auth.getUsersAsync(
                ImmutableList.<UserIdentifier>of(new UidIdentifier(user1.getUid()),
                    new UidIdentifier(user2.getUid()), new UidIdentifier(user3.getUid())))
            .get();

    assertTrue(getUsersResult.getUsers().isEmpty());
    assertEquals(3, getUsersResult.getNotFound().size());
  }

  @Test
  public void testDeleteExistingAndNonExistingUsers() throws Exception {
    UserRecord user1 = newUserWithParams();

    DeleteUsersResult deleteUsersResult =
        slowDeleteUsersAsync(ImmutableList.of(user1.getUid(), "uid-that-doesnt-exist")).get();

    assertEquals(2, deleteUsersResult.getSuccessCount());
    assertEquals(0, deleteUsersResult.getFailureCount());
    assertTrue(deleteUsersResult.getErrors().isEmpty());

    GetUsersResult getUsersResult =
        auth.getUsersAsync(ImmutableList.<UserIdentifier>of(new UidIdentifier(user1.getUid()),
                               new UidIdentifier("uid-that-doesnt-exist")))
            .get();

    assertTrue(getUsersResult.getUsers().isEmpty());
    assertEquals(2, getUsersResult.getNotFound().size());
  }

  @Test
  public void testDeleteUsersIsIdempotent() throws Exception {
    UserRecord user1 = newUserWithParams();

    DeleteUsersResult result = slowDeleteUsersAsync(ImmutableList.of(user1.getUid())).get();

    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());
    assertTrue(result.getErrors().isEmpty());

    // Delete the user again to ensure that everything still counts as a success.
    result = slowDeleteUsersAsync(ImmutableList.of(user1.getUid())).get();

    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());
    assertTrue(result.getErrors().isEmpty());
  }

  /**
   * The {@code batchDelete} endpoint has a rate limit of 1 QPS. Use this test
   * helper to ensure you don't exceed the quota.
   */
  // TODO(rsgowman): When/if the rate limit is relaxed, eliminate this helper.
  private ApiFuture<DeleteUsersResult> slowDeleteUsersAsync(List<String> uids) throws Exception {
    TimeUnit.SECONDS.sleep(1);
    return auth.deleteUsersAsync(uids);
  }

  @Test
  public void testCreateUserWithParams() throws Exception {
    RandomUser randomUser = RandomUser.create();
    String phone = randomPhoneNumber();
    UserRecord.CreateRequest user = new UserRecord.CreateRequest()
        .setUid(randomUser.uid)
        .setEmail(randomUser.email)
        .setPhoneNumber(phone)
        .setDisplayName("Random User")
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("password");

    UserRecord userRecord = temporaryUser.create(user);
    assertEquals(randomUser.uid, userRecord.getUid());
    assertEquals("Random User", userRecord.getDisplayName());
    assertEquals(randomUser.email, userRecord.getEmail());
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

    checkRecreateUser(randomUser.uid);
  }

  @Test
  public void testUserLifecycle() throws Exception {
    // Create user
    UserRecord userRecord = auth.createUserAsync(new UserRecord.CreateRequest()).get();
    String uid = userRecord.getUid();

    // Get user
    userRecord = auth.getUserAsync(userRecord.getUid()).get();
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getTenantId());
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
    RandomUser randomUser = RandomUser.create();
    String phone = randomPhoneNumber();
    UserRecord.UpdateRequest request = userRecord.updateRequest()
        .setDisplayName("Updated Name")
        .setEmail(randomUser.email)
        .setPhoneNumber(phone)
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("secret");
    userRecord = auth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getTenantId());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(randomUser.email, userRecord.getEmail());
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
    assertNull(userRecord.getTenantId());
    assertNull(userRecord.getDisplayName());
    assertEquals(randomUser.email, userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());
    assertEquals(1, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Delete user
    auth.deleteUserAsync(userRecord.getUid()).get();
    assertUserDoesNotExist(auth, userRecord.getUid());
  }

  @Test
  public void testLastRefreshTime() throws Exception {
    RandomUser user = RandomUser.create();
    UserRecord newUserRecord = auth.createUser(new UserRecord.CreateRequest()
                                                   .setUid(user.uid)
                                                   .setEmail(user.email)
                                                   .setEmailVerified(false)
                                                   .setPassword("password"));

    try {
      // New users should not have a lastRefreshTimestamp set.
      assertEquals(0, newUserRecord.getUserMetadata().getLastRefreshTimestamp());

      // Login to cause the lastRefreshTimestamp to be set.
      signInWithPassword(newUserRecord.getEmail(), "password");

      UserRecord userRecord = auth.getUser(newUserRecord.getUid());

      // Ensure the lastRefreshTimestamp is approximately "now" (with a tollerance of 10 minutes).
      long now = System.currentTimeMillis();
      long tollerance = TimeUnit.MINUTES.toMillis(10);
      long lastRefreshTimestamp = userRecord.getUserMetadata().getLastRefreshTimestamp();
      assertTrue(now - tollerance <= lastRefreshTimestamp);
      assertTrue(lastRefreshTimestamp <= now + tollerance);
    } finally {
      auth.deleteUser(newUserRecord.getUid());
    }
  }

  @Test
  public void testListUsers() throws Exception {
    final List<String> uids = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      UserRecord.CreateRequest createRequest =
          new UserRecord.CreateRequest().setPassword("password");
      uids.add(temporaryUser.create(createRequest).getUid());
    }

    // Test list by batches
    final AtomicInteger collected = new AtomicInteger(0);
    ListUsersPage page = auth.listUsersAsync(null).get();
    while (page != null) {
      for (ExportedUserRecord user : page.getValues()) {
        if (uids.contains(user.getUid())) {
          collected.incrementAndGet();
          assertNotNull("Missing passwordHash field. A common cause would be "
              + "forgetting to add the \"Firebase Authentication Admin\" permission. See "
              + "instructions in CONTRIBUTING.md", user.getPasswordHash());
          assertNotNull(user.getPasswordSalt());
          assertNull(user.getTenantId());
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
        assertNull(user.getTenantId());
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
            assertNull(user.getTenantId());
          }
        }
        semaphore.release();
      }
    }, MoreExecutors.directExecutor());
    semaphore.acquire();
    assertEquals(uids.size(), collected.get());
    assertNull(error.get());
  }

  @Test
  public void testCustomClaims() throws Exception {
    UserRecord userRecord = temporaryUser.create(new UserRecord.CreateRequest());
    String uid = userRecord.getUid();

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
        .setCredentials(GoogleCredentials.create(token))
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
      assertEquals(RevocationCheckDecorator.ID_TOKEN_REVOKED_ERROR,
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
      assertEquals(RevocationCheckDecorator.SESSION_COOKIE_REVOKED_ERROR,
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
    RandomUser randomUser = RandomUser.create();
    ImportUserRecord user = ImportUserRecord.builder()
        .setUid(randomUser.uid)
        .setEmail(randomUser.email)
        .build();

    UserImportResult result = auth.importUsersAsync(ImmutableList.of(user)).get();
    temporaryUser.registerUid(randomUser.uid);
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());

    UserRecord savedUser = auth.getUserAsync(randomUser.uid).get();
    assertEquals(randomUser.email, savedUser.getEmail());
  }

  @Test
  public void testImportUsersWithPassword() throws Exception {
    RandomUser randomUser = RandomUser.create();
    final byte[] passwordHash = BaseEncoding.base64().decode(
        "V358E8LdWJXAO7muq0CufVpEOXaj8aFiC7T/rcaGieN04q/ZPJ08WhJEHGjj9lz/2TT+/86N5VjVoc5DdBhBiw==");
    ImportUserRecord user = ImportUserRecord.builder()
        .setUid(randomUser.uid)
        .setEmail(randomUser.email)
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
    temporaryUser.registerUid(randomUser.uid);
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());

    UserRecord savedUser = auth.getUserAsync(randomUser.uid).get();
    assertEquals(randomUser.email, savedUser.getEmail());
    String idToken = signInWithPassword(randomUser.email, "password");
    assertFalse(Strings.isNullOrEmpty(idToken));
  }

  @Test
  public void testGeneratePasswordResetLink() throws Exception {
    RandomUser user = RandomUser.create();
    temporaryUser.create(new UserRecord.CreateRequest()
        .setUid(user.uid)
        .setEmail(user.email)
        .setEmailVerified(false)
        .setPassword("password"));
    String link = auth.generatePasswordResetLink(user.email, ActionCodeSettings.builder()
        .setUrl(ACTION_LINK_CONTINUE_URL)
        .setHandleCodeInApp(false)
        .build());
    Map<String, String> linkParams = parseLinkParameters(link);
    assertEquals(ACTION_LINK_CONTINUE_URL, linkParams.get("continueUrl"));
    String email = resetPassword(user.email, "password", "newpassword",
        linkParams.get("oobCode"));
    assertEquals(user.email, email);
    // Password reset also verifies the user's email
    assertTrue(auth.getUser(user.uid).isEmailVerified());
  }

  @Test
  public void testGenerateEmailVerificationResetLink() throws Exception {
    RandomUser user = RandomUser.create();
    temporaryUser.create(new UserRecord.CreateRequest()
        .setUid(user.uid)
        .setEmail(user.email)
        .setEmailVerified(false)
        .setPassword("password"));
    String link = auth.generateEmailVerificationLink(user.email, ActionCodeSettings.builder()
        .setUrl(ACTION_LINK_CONTINUE_URL)
        .setHandleCodeInApp(false)
        .build());
    Map<String, String> linkParams = parseLinkParameters(link);
    assertEquals(ACTION_LINK_CONTINUE_URL, linkParams.get("continueUrl"));
    // There doesn't seem to be a public API for verifying an email, so we cannot do a more
    // thorough test here.
    assertEquals("verifyEmail", linkParams.get("mode"));
  }

  @Test
  public void testGenerateSignInWithEmailLink() throws Exception {
    RandomUser user = RandomUser.create();
    temporaryUser.create(new UserRecord.CreateRequest()
        .setUid(user.uid)
        .setEmail(user.email)
        .setEmailVerified(false)
        .setPassword("password"));
    String link = auth.generateSignInWithEmailLink(user.email, ActionCodeSettings.builder()
        .setUrl(ACTION_LINK_CONTINUE_URL)
        .setHandleCodeInApp(false)
        .build());
    Map<String, String> linkParams = parseLinkParameters(link);
    assertEquals(ACTION_LINK_CONTINUE_URL, linkParams.get("continueUrl"));
    String idToken = signInWithEmailLink(user.email, linkParams.get("oobCode"));
    assertFalse(Strings.isNullOrEmpty(idToken));
    assertTrue(auth.getUser(user.uid).isEmailVerified());
  }

  @Test
  public void testOidcProviderConfigLifecycle() throws Exception {
    // Create provider config
    String providerId = "oidc.provider-id";
    OidcProviderConfig config = temporaryProviderConfig.createOidcProviderConfig(
        new OidcProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setDisplayName("DisplayName")
            .setEnabled(true)
            .setClientId("ClientId")
            .setIssuer("https://oidc.com/issuer"));
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("ClientId", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());

    // Get provider config
    config = auth.getOidcProviderConfigAsync(providerId).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("ClientId", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());

    // Update provider config
    OidcProviderConfig.UpdateRequest updateRequest =
        new OidcProviderConfig.UpdateRequest(providerId)
            .setDisplayName("NewDisplayName")
            .setEnabled(false)
            .setClientId("NewClientId")
            .setIssuer("https://oidc.com/new-issuer");
    config = auth.updateOidcProviderConfigAsync(updateRequest).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("NewDisplayName", config.getDisplayName());
    assertFalse(config.isEnabled());
    assertEquals("NewClientId", config.getClientId());
    assertEquals("https://oidc.com/new-issuer", config.getIssuer());

    // Delete provider config
    temporaryProviderConfig.deleteOidcProviderConfig(providerId);
    ProviderConfigTestUtils.assertOidcProviderConfigDoesNotExist(auth, providerId);
  }

  @Test
  public void testListOidcProviderConfigs() throws Exception {
    final List<String> providerIds = new ArrayList<>();

    // Create provider configs
    for (int i = 0; i < 3; i++) {
      String providerId = "oidc.provider-id" + i;
      providerIds.add(providerId);
      temporaryProviderConfig.createOidcProviderConfig(
          new OidcProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setClientId("CLIENT_ID")
            .setIssuer("https://oidc.com/issuer"));
    }

    // Test list by batches
    final AtomicInteger collected = new AtomicInteger(0);
    ListProviderConfigsPage<OidcProviderConfig> page =
        auth.listOidcProviderConfigsAsync(null).get();
    while (page != null) {
      for (OidcProviderConfig providerConfig : page.getValues()) {
        if (checkOidcProviderConfig(providerIds, providerConfig)) {
          collected.incrementAndGet();
        }
      }
      page = page.getNextPage();
    }
    assertEquals(providerIds.size(), collected.get());

    // Test iterate all
    collected.set(0);
    page = auth.listOidcProviderConfigsAsync(null).get();
    for (OidcProviderConfig providerConfig : page.iterateAll()) {
      if (checkOidcProviderConfig(providerIds, providerConfig)) {
        collected.incrementAndGet();
      }
    }
    assertEquals(providerIds.size(), collected.get());

    // Test iterate async
    collected.set(0);
    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<Throwable> error = new AtomicReference<>();
    ApiFuture<ListProviderConfigsPage<OidcProviderConfig>> pageFuture =
        auth.listOidcProviderConfigsAsync(null);
    ApiFutures.addCallback(
        pageFuture,
        new ApiFutureCallback<ListProviderConfigsPage<OidcProviderConfig>>() {
          @Override
          public void onFailure(Throwable t) {
            error.set(t);
            semaphore.release();
          }

          @Override
          public void onSuccess(ListProviderConfigsPage<OidcProviderConfig> result) {
            for (OidcProviderConfig providerConfig : result.iterateAll()) {
              if (checkOidcProviderConfig(providerIds, providerConfig)) {
                collected.incrementAndGet();
              }
            }
            semaphore.release();
          }
        }, MoreExecutors.directExecutor());
    semaphore.acquire();
    assertEquals(providerIds.size(), collected.get());
    assertNull(error.get());
  }

  @Test
  public void testSamlProviderConfigLifecycle() throws Exception {
    // Create provider config
    String providerId = "saml.provider-id";
    SamlProviderConfig config = temporaryProviderConfig.createSamlProviderConfig(
        new SamlProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setDisplayName("DisplayName")
            .setEnabled(true)
            .setIdpEntityId("IDP_ENTITY_ID")
            .setSsoUrl("https://example.com/login")
            .addX509Certificate("certificate1")
            .addX509Certificate("certificate2")
            .setRpEntityId("RP_ENTITY_ID")
            .setCallbackUrl("https://projectId.firebaseapp.com/__/auth/handler"));
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("IDP_ENTITY_ID", config.getIdpEntityId());
    assertEquals("https://example.com/login", config.getSsoUrl());
    assertEquals(ImmutableList.of("certificate1", "certificate2"), config.getX509Certificates());
    assertEquals("RP_ENTITY_ID", config.getRpEntityId());
    assertEquals("https://projectId.firebaseapp.com/__/auth/handler", config.getCallbackUrl());

    config = auth.getSamlProviderConfig(providerId);
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("IDP_ENTITY_ID", config.getIdpEntityId());
    assertEquals("https://example.com/login", config.getSsoUrl());
    assertEquals(ImmutableList.of("certificate1", "certificate2"), config.getX509Certificates());
    assertEquals("RP_ENTITY_ID", config.getRpEntityId());
    assertEquals("https://projectId.firebaseapp.com/__/auth/handler", config.getCallbackUrl());

    // Update provider config
    SamlProviderConfig.UpdateRequest updateRequest =
        new SamlProviderConfig.UpdateRequest(providerId)
            .setDisplayName("NewDisplayName")
            .setEnabled(false)
            .addX509Certificate("certificate");
    config = auth.updateSamlProviderConfigAsync(updateRequest).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("NewDisplayName", config.getDisplayName());
    assertFalse(config.isEnabled());
    assertEquals(ImmutableList.of("certificate"), config.getX509Certificates());

    // Delete provider config
    temporaryProviderConfig.deleteSamlProviderConfig(providerId);
    ProviderConfigTestUtils.assertSamlProviderConfigDoesNotExist(auth, providerId);
  }

  @Test
  public void testListSamlProviderConfigs() throws Exception {
    final List<String> providerIds = new ArrayList<>();

    // Create provider configs
    for (int i = 0; i < 3; i++) {
      String providerId = "saml.provider-id" + i;
      providerIds.add(providerId);
      temporaryProviderConfig.createSamlProviderConfig(
          new SamlProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setIdpEntityId("IDP_ENTITY_ID")
            .setSsoUrl("https://example.com/login")
            .addX509Certificate("certificate")
            .setRpEntityId("RP_ENTITY_ID")
            .setCallbackUrl("https://projectId.firebaseapp.com/__/auth/handler"));
    }

    // Test list by batches
    final AtomicInteger collected = new AtomicInteger(0);
    ListProviderConfigsPage<SamlProviderConfig> page =
        auth.listSamlProviderConfigsAsync(null).get();
    while (page != null) {
      for (SamlProviderConfig providerConfig : page.getValues()) {
        if (checkSamlProviderConfig(providerIds, providerConfig)) {
          collected.incrementAndGet();
        }
      }
      page = page.getNextPage();
    }
    assertEquals(providerIds.size(), collected.get());

    // Test iterate all
    collected.set(0);
    page = auth.listSamlProviderConfigsAsync(null).get();
    for (SamlProviderConfig providerConfig : page.iterateAll()) {
      if (checkSamlProviderConfig(providerIds, providerConfig)) {
        collected.incrementAndGet();
      }
    }
    assertEquals(providerIds.size(), collected.get());

    // Test iterate async
    collected.set(0);
    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<Throwable> error = new AtomicReference<>();
    ApiFuture<ListProviderConfigsPage<SamlProviderConfig>> pageFuture =
        auth.listSamlProviderConfigsAsync(null);
    ApiFutures.addCallback(
        pageFuture,
        new ApiFutureCallback<ListProviderConfigsPage<SamlProviderConfig>>() {
          @Override
          public void onFailure(Throwable t) {
            error.set(t);
            semaphore.release();
          }

          @Override
          public void onSuccess(ListProviderConfigsPage<SamlProviderConfig> result) {
            for (SamlProviderConfig providerConfig : result.iterateAll()) {
              if (checkSamlProviderConfig(providerIds, providerConfig)) {
                collected.incrementAndGet();
              }
            }
            semaphore.release();
          }
        }, MoreExecutors.directExecutor());
    semaphore.acquire();
    assertEquals(providerIds.size(), collected.get());
    assertNull(error.get());
  }

  private Map<String, String> parseLinkParameters(String link) throws Exception {
    Map<String, String> result = new HashMap<>();
    int queryBegin = link.indexOf('?');
    if (queryBegin != -1) {
      String[] segments = link.substring(queryBegin + 1).split("&");
      for (String segment : segments) {
        int equalSign = segment.indexOf('=');
        String key = segment.substring(0, equalSign);
        String value = segment.substring(equalSign + 1);
        result.put(key, URLDecoder.decode(value, "UTF-8"));
      }
    }
    return result;
  }

  static String randomPhoneNumber() {
    Random random = new Random();
    StringBuilder builder = new StringBuilder("+1");
    for (int i = 0; i < 10; i++) {
      builder.append(random.nextInt(10));
    }
    return builder.toString();
  }

  private String signInWithCustomToken(String customToken) throws IOException {
    return signInWithCustomToken(customToken, null);
  }

  private String signInWithCustomToken(
      String customToken, @Nullable String tenantId) throws IOException {
    final GenericUrl url = new GenericUrl(VERIFY_CUSTOM_TOKEN_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    ImmutableMap.Builder<String, Object> content = ImmutableMap.<String, Object>builder();
    content.put("token", customToken);
    content.put("returnSecureToken", true);
    if (tenantId != null) {
      content.put("tenantId", tenantId);
    }
    HttpRequest request = transport.createRequestFactory().buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content.build()));
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
    GenericUrl url = new GenericUrl(VERIFY_PASSWORD_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    Map<String, Object> content = ImmutableMap.<String, Object>of(
        "email", email, "password", password, "returnSecureToken", true);
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

  private String resetPassword(
      String email, String oldPassword, String newPassword, String oobCode) throws IOException {
    GenericUrl url = new GenericUrl(RESET_PASSWORD_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    Map<String, Object> content = ImmutableMap.<String, Object>of(
        "email", email, "oldPassword", oldPassword, "newPassword", newPassword, "oobCode", oobCode);
    HttpRequest request = transport.createRequestFactory().buildPostRequest(url,
        new JsonHttpContent(jsonFactory, content));
    request.setParser(new JsonObjectParser(jsonFactory));
    HttpResponse response = request.execute();
    try {
      GenericJson json = response.parseAs(GenericJson.class);
      return json.get("email").toString();
    } finally {
      response.disconnect();
    }
  }

  private String signInWithEmailLink(
      String email, String oobCode) throws IOException {
    GenericUrl url = new GenericUrl(EMAIL_LINK_SIGN_IN_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    Map<String, Object> content = ImmutableMap.<String, Object>of(
        "email", email, "oobCode", oobCode);
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

  private void checkRecreateUser(String uid) throws Exception {
    try {
      auth.createUserAsync(new UserRecord.CreateRequest().setUid(uid)).get();
      fail("No error thrown for creating user with existing ID");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals("uid-already-exists", ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  static class RandomUser {
    final String uid;
    final String email;

    private RandomUser(String uid, String email) {
      this.uid = uid;
      this.email = email;
    }

    static RandomUser create() {
      final String uid = UUID.randomUUID().toString().replaceAll("-", "");
      final String email = ("test" + uid.substring(0, 12) + "@example."
          + uid.substring(12) + ".com").toLowerCase();
      return new RandomUser(uid, email);
    }
  }

  private boolean checkOidcProviderConfig(List<String> providerIds, OidcProviderConfig config) {
    if (providerIds.contains(config.getProviderId())) {
      assertEquals("CLIENT_ID", config.getClientId());
      assertEquals("https://oidc.com/issuer", config.getIssuer());
      return true;
    }
    return false;
  }

  private boolean checkSamlProviderConfig(List<String> providerIds, SamlProviderConfig config) {
    if (providerIds.contains(config.getProviderId())) {
      assertEquals("IDP_ENTITY_ID", config.getIdpEntityId());
      assertEquals("https://example.com/login", config.getSsoUrl());
      assertEquals(ImmutableList.of("certificate"), config.getX509Certificates());
      assertEquals("RP_ENTITY_ID", config.getRpEntityId());
      assertEquals("https://projectId.firebaseapp.com/__/auth/handler", config.getCallbackUrl());
      return true;
    }
    return false;
  }

  private static void assertUserDoesNotExist(AbstractFirebaseAuth firebaseAuth, String uid)
      throws Exception {
    try {
      firebaseAuth.getUserAsync(uid).get();
      fail("No error thrown for getting a user which was expected to be absent.");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  static UserRecord newUserWithParams() throws Exception {
    return newUserWithParams(auth);
  }

  static UserRecord newUserWithParams(FirebaseAuth auth) throws Exception {
    // TODO(rsgowman): This function could be used throughout this file (similar to the other
    // ports).
    RandomUser randomUser = RandomUser.create();
    return auth.createUser(new UserRecord.CreateRequest()
                               .setUid(randomUser.uid)
                               .setEmail(randomUser.email)
                               .setPhoneNumber(randomPhoneNumber())
                               .setDisplayName("Random User")
                               .setPhotoUrl("https://example.com/photo.png")
                               .setPassword("password"));
  }

  /**
   * Creates temporary Firebase user accounts for testing, and deletes them at the end of each
   * test case.
   */
  private static final class TemporaryUser extends ExternalResource {

    private final List<String> users = new ArrayList<>();

    public UserRecord create(UserRecord.CreateRequest request) throws FirebaseAuthException {
      UserRecord user = auth.createUser(request);
      registerUid(user.getUid());
      return user;
    }

    public synchronized void registerUid(String uid) {
      users.add(uid);
    }

    @Override
    protected synchronized void after() {
      for (String uid : users) {
        try {
          auth.deleteUser(uid);
        } catch (Exception ignore) {
          // Ignore
        }
      }

      users.clear();
    }
  }
}
