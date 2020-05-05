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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TenantAwareFirebaseAuthIT {

  private static final String VERIFY_CUSTOM_TOKEN_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken";
  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
  private static final HttpTransport transport = Utils.getDefaultTransport();

  private static FirebaseAuth auth;
  private static TenantManager tenantManager;
  private static TenantAwareFirebaseAuth tenantAwareAuth;
  private static String tenantId;

  @BeforeClass
  public static void setUpClass() throws Exception {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    auth = FirebaseAuth.getInstance(masterApp);
    tenantManager = auth.getTenantManager();
    Tenant.CreateRequest tenantCreateRequest =
        new Tenant.CreateRequest().setDisplayName("DisplayName");
    tenantId = tenantManager.createTenant(tenantCreateRequest).getTenantId();
    tenantAwareAuth = tenantManager.getAuthForTenant(tenantId);
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    tenantManager.deleteTenant(tenantId);
  }

  @Test
  public void testUserLifecycle() throws Exception {
    // Create user
    UserRecord userRecord = tenantAwareAuth.createUserAsync(new UserRecord.CreateRequest()).get();
    String uid = userRecord.getUid();

    // Get user
    userRecord = tenantAwareAuth.getUserAsync(userRecord.getUid()).get();
    assertEquals(uid, userRecord.getUid());
    assertEquals(tenantId, userRecord.getTenantId());
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
    userRecord = tenantAwareAuth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertEquals(tenantId, userRecord.getTenantId());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(randomUser.email, userRecord.getEmail());
    assertEquals(phone, userRecord.getPhoneNumber());
    assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());
    assertEquals(2, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Get user by email
    userRecord = tenantAwareAuth.getUserByEmailAsync(userRecord.getEmail()).get();
    assertEquals(uid, userRecord.getUid());

    // Disable user and remove properties
    request = userRecord.updateRequest()
        .setPhotoUrl(null)
        .setDisplayName(null)
        .setPhoneNumber(null)
        .setDisabled(true);
    userRecord = tenantAwareAuth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertEquals(tenantId, userRecord.getTenantId());
    assertNull(userRecord.getDisplayName());
    assertEquals(randomUser.email, userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());
    assertEquals(1, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Delete user
    tenantAwareAuth.deleteUserAsync(userRecord.getUid()).get();
    assertUserDoesNotExist(tenantAwareAuth, userRecord.getUid());
  }

  @Test
  public void testListUsers() throws Exception {
    final List<String> uids = new ArrayList<>();

    try {
      for (int i = 0; i < 3; i++) {
        UserRecord.CreateRequest createRequest =
            new UserRecord.CreateRequest().setPassword("password");
        uids.add(tenantAwareAuth.createUserAsync(createRequest).get().getUid());
      }

      // Test list by batches
      final AtomicInteger collected = new AtomicInteger(0);
      ListUsersPage page = tenantAwareAuth.listUsersAsync(null).get();
      while (page != null) {
        for (ExportedUserRecord user : page.getValues()) {
          if (uids.contains(user.getUid())) {
            collected.incrementAndGet();
            assertNotNull("Missing passwordHash field. A common cause would be "
                + "forgetting to add the \"Firebase Authentication Admin\" permission. See "
                + "instructions in CONTRIBUTING.md", user.getPasswordHash());
            assertNotNull(user.getPasswordSalt());
            assertEquals(tenantId, user.getTenantId());
          }
        }
        page = page.getNextPage();
      }
      assertEquals(uids.size(), collected.get());

      // Test iterate all
      collected.set(0);
      page = tenantAwareAuth.listUsersAsync(null).get();
      for (ExportedUserRecord user : page.iterateAll()) {
        if (uids.contains(user.getUid())) {
          collected.incrementAndGet();
          assertNotNull(user.getPasswordHash());
          assertNotNull(user.getPasswordSalt());
          assertEquals(tenantId, user.getTenantId());
        }
      }
      assertEquals(uids.size(), collected.get());

      // Test iterate async
      collected.set(0);
      final Semaphore semaphore = new Semaphore(0);
      final AtomicReference<Throwable> error = new AtomicReference<>();
      ApiFuture<ListUsersPage> pageFuture = tenantAwareAuth.listUsersAsync(null);
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
              assertEquals(tenantId, user.getTenantId());
            }
          }
          semaphore.release();
        }
      }, MoreExecutors.directExecutor());
      semaphore.acquire();
      assertEquals(uids.size(), collected.get());
      assertNull(error.get());
    } finally {
      for (String uid : uids) {
        tenantAwareAuth.deleteUserAsync(uid).get();
      }
    }
  }

  @Test
  public void testGetUserWithMultipleTenantIds() throws Exception {
    // Create second tenant.
    Tenant.CreateRequest tenantCreateRequest =
        new Tenant.CreateRequest().setDisplayName("DisplayName2");
    String tenantId2 = tenantManager.createTenant(tenantCreateRequest).getTenantId();

    // Create three users (one without a tenant ID, and two with different tenant IDs).
    UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest();
    UserRecord nonTenantUserRecord = auth.createUser(createRequest);
    UserRecord tenantUserRecord1 = tenantAwareAuth.createUser(createRequest);
    TenantAwareFirebaseAuth tenantAwareAuth2 = auth.getTenantManager().getAuthForTenant(tenantId2);
    UserRecord tenantUserRecord2 = tenantAwareAuth2.createUser(createRequest);

    // Make sure only non-tenant users can be fetched using the standard client.
    assertNotNull(auth.getUser(nonTenantUserRecord.getUid()));
    assertUserDoesNotExist(auth, tenantUserRecord1.getUid());
    assertUserDoesNotExist(auth, tenantUserRecord2.getUid());

    // Make sure tenant-aware client cannot fetch users outside that tenant.
    assertUserDoesNotExist(tenantAwareAuth, nonTenantUserRecord.getUid());
    assertUserDoesNotExist(tenantAwareAuth, tenantUserRecord2.getUid());
    assertUserDoesNotExist(tenantAwareAuth2, nonTenantUserRecord.getUid());
    assertUserDoesNotExist(tenantAwareAuth2, tenantUserRecord1.getUid());

    // Make sure tenant-aware client can fetch users under that tenant.
    assertNotNull(tenantAwareAuth.getUser(tenantUserRecord1.getUid()));
    assertNotNull(tenantAwareAuth2.getUser(tenantUserRecord2.getUid()));

    // Delete second tenant.
    tenantManager.deleteTenant(tenantId2);
  }

  @Test
  public void testCustomToken() throws Exception {
    String customToken = tenantAwareAuth.createCustomTokenAsync("user1").get();
    String idToken = signInWithCustomToken(customToken, tenantId);
    FirebaseToken decoded = tenantAwareAuth.verifyIdTokenAsync(idToken).get();
    assertEquals("user1", decoded.getUid());
    assertEquals(tenantId, decoded.getTenantId());
  }

  @Test
  public void testVerifyTokenWithWrongTenantAwareClient() throws Exception {
    String customToken = tenantAwareAuth.createCustomTokenAsync("user").get();
    String idToken = signInWithCustomToken(customToken, tenantId);

    try {
      auth.getTenantManager().getAuthForTenant("OTHER").verifyIdTokenAsync(idToken).get();
      fail("No error thrown for verifying a token with the wrong tenant-aware client");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.TENANT_ID_MISMATCH_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testOidcProviderConfigLifecycle() throws Exception {
    // Create config provider
    String providerId = "oidc.provider-id";
    OidcProviderConfig.CreateRequest createRequest =
        new OidcProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setDisplayName("DisplayName")
            .setEnabled(true)
            .setClientId("ClientId")
            .setIssuer("https://oidc.com/issuer");
    OidcProviderConfig config = tenantAwareAuth.createOidcProviderConfigAsync(createRequest).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertEquals("ClientId", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());

    try {
      // Get config provider
      config = tenantAwareAuth.getOidcProviderConfigAsync(providerId).get();
      assertEquals(providerId, config.getProviderId());
      assertEquals("DisplayName", config.getDisplayName());
      assertEquals("ClientId", config.getClientId());
      assertEquals("https://oidc.com/issuer", config.getIssuer());

      // Update config provider
      OidcProviderConfig.UpdateRequest updateRequest =
          new OidcProviderConfig.UpdateRequest(providerId)
              .setDisplayName("NewDisplayName")
              .setEnabled(false)
              .setClientId("NewClientId")
              .setIssuer("https://oidc.com/new-issuer");
      config = tenantAwareAuth.updateOidcProviderConfigAsync(updateRequest).get();
      assertEquals(providerId, config.getProviderId());
      assertEquals("NewDisplayName", config.getDisplayName());
      assertFalse(config.isEnabled());
      assertEquals("NewClientId", config.getClientId());
      assertEquals("https://oidc.com/new-issuer", config.getIssuer());
    } finally {
      // Delete config provider
      tenantAwareAuth.deleteProviderConfigAsync(providerId).get();
      assertOidcProviderConfigDoesNotExist(tenantAwareAuth, providerId);
    }
  }

  @Test
  public void testListOidcProviderConfigs() throws Exception {
    final List<String> providerIds = new ArrayList<>();
    try {
      // Create provider configs
      for (int i = 0; i < 3; i++) {
        String providerId = "oidc.provider-id" + i;
        providerIds.add(providerId);
        OidcProviderConfig.CreateRequest createRequest = new OidcProviderConfig.CreateRequest()
            .setProviderId(providerId)
            .setClientId("CLIENT_ID")
            .setIssuer("https://oidc.com/issuer");
        tenantAwareAuth.createOidcProviderConfig(createRequest);
      }

      // List provider configs
      final AtomicInteger collected = new AtomicInteger(0);
      ListProviderConfigsPage<OidcProviderConfig> page =
          tenantAwareAuth.listOidcProviderConfigsAsync(null).get();
      for (OidcProviderConfig providerConfig : page.iterateAll()) {
        if (providerIds.contains(providerConfig.getProviderId())) {
          collected.incrementAndGet();
          assertEquals("CLIENT_ID", providerConfig.getClientId());
          assertEquals("https://oidc.com/issuer", providerConfig.getIssuer());
        }
      }
      assertEquals(providerIds.size(), collected.get());

    } finally {
      // Delete provider configs
      for (String providerId : providerIds) {
        tenantAwareAuth.deleteProviderConfigAsync(providerId).get();
      }
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

  private static class RandomUser {
    private final String uid;
    private final String email;

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

  private static void assertOidcProviderConfigDoesNotExist(
      AbstractFirebaseAuth firebaseAuth, String providerId) throws Exception {
    try {
      firebaseAuth.getOidcProviderConfigAsync(providerId).get();
      fail("No error thrown for getting a deleted provider config");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.CONFIGURATION_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  private static void assertUserDoesNotExist(AbstractFirebaseAuth firebaseAuth, String uid)
      throws Exception {
    try {
      firebaseAuth.getUserAsync(uid).get();
      fail("No error thrown for getting a user which was expected to be absent");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }
}
