/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.auth.multitenancy;

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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ListProviderConfigsPage;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.OidcProviderConfig;
import com.google.firebase.auth.ProviderConfigTestUtils;
import com.google.firebase.auth.ProviderConfigTestUtils.TemporaryProviderConfig;
import com.google.firebase.auth.SamlProviderConfig;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserTestUtils;
import com.google.firebase.auth.UserTestUtils.RandomUser;
import com.google.firebase.auth.UserTestUtils.TemporaryUser;
import com.google.firebase.internal.Nullable;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareFirebaseAuthIT {

  private static final String VERIFY_CUSTOM_TOKEN_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken";
  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
  private static final HttpTransport transport = Utils.getDefaultTransport();

  private static TenantManager tenantManager;
  private static TenantAwareFirebaseAuth tenantAwareAuth;
  private static String tenantId;

  @Rule public final TemporaryUser temporaryUser = new TemporaryUser(tenantAwareAuth);
  @Rule public final TemporaryProviderConfig temporaryProviderConfig =
      new TemporaryProviderConfig(tenantAwareAuth);

  @BeforeClass
  public static void setUpClass() throws Exception {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    tenantManager = FirebaseAuth.getInstance(masterApp).getTenantManager();
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
    UserRecord userRecord = temporaryUser.create(new UserRecord.CreateRequest());
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
    RandomUser randomUser = UserTestUtils.generateRandomUserInfo();
    UserRecord.UpdateRequest request = userRecord.updateRequest()
        .setDisplayName("Updated Name")
        .setEmail(randomUser.getEmail())
        .setPhoneNumber(randomUser.getPhoneNumber())
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("secret");
    userRecord = tenantAwareAuth.updateUserAsync(request).get();
    assertEquals(uid, userRecord.getUid());
    assertEquals(tenantId, userRecord.getTenantId());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(randomUser.getEmail(), userRecord.getEmail());
    assertEquals(randomUser.getPhoneNumber(), userRecord.getPhoneNumber());
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
    assertEquals(randomUser.getEmail(), userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());
    assertEquals(1, userRecord.getProviderData().length);
    assertTrue(userRecord.getCustomClaims().isEmpty());

    // Delete user
    tenantAwareAuth.deleteUserAsync(userRecord.getUid()).get();
    UserTestUtils.assertUserDoesNotExist(tenantAwareAuth, userRecord.getUid());
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
      tenantManager.getAuthForTenant("OTHER").verifyIdTokenAsync(idToken).get();
      fail("No error thrown for verifying a token with the wrong tenant-aware client");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(AuthErrorCode.TENANT_ID_MISMATCH,
          ((FirebaseAuthException) e.getCause()).getAuthErrorCode());
    }

    // Verifies with FirebaseAuth
    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
    assertEquals("user", decoded.getUid());
    assertEquals(tenantId, decoded.getTenantId());
  }

  @Test
  public void testOidcProviderConfigLifecycle() throws Exception {
    // Create provider config
    String providerId = "oidc.provider-id";
    OidcProviderConfig config =
        temporaryProviderConfig.createOidcProviderConfig(
          new OidcProviderConfig.CreateRequest()
              .setProviderId(providerId)
              .setDisplayName("DisplayName")
              .setEnabled(true)
              .setClientId("ClientId")
              .setIssuer("https://oidc.com/issuer"));
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertEquals("ClientId", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());

    // Get provider config
    config = tenantAwareAuth.getOidcProviderConfigAsync(providerId).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("DisplayName", config.getDisplayName());
    assertEquals("ClientId", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());

    // Update provider config
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

    // Delete provider config
    temporaryProviderConfig.deleteOidcProviderConfig(providerId);
    ProviderConfigTestUtils.assertOidcProviderConfigDoesNotExist(tenantAwareAuth, providerId);
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

    // List provider configs
    // NOTE: We do not need to test all of the different ways we can iterate over the provider
    // configs, since this testing is already performed in FirebaseAuthIT with the tenant-agnostic
    // tests.
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

    config = tenantAwareAuth.getSamlProviderConfig(providerId);
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
    config = tenantAwareAuth.updateSamlProviderConfigAsync(updateRequest).get();
    assertEquals(providerId, config.getProviderId());
    assertEquals("NewDisplayName", config.getDisplayName());
    assertFalse(config.isEnabled());
    assertEquals(ImmutableList.of("certificate"), config.getX509Certificates());

    // Delete provider config
    temporaryProviderConfig.deleteSamlProviderConfig(providerId);
    ProviderConfigTestUtils.assertSamlProviderConfigDoesNotExist(tenantAwareAuth, providerId);
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

    // List provider configs
    // NOTE: We do not need to test all of the different ways we can iterate over the provider
    // configs, since this testing is already performed in FirebaseAuthIT with the tenant-agnostic
    // tests.
    final AtomicInteger collected = new AtomicInteger(0);
    ListProviderConfigsPage<SamlProviderConfig> page =
        tenantAwareAuth.listSamlProviderConfigsAsync(null).get();
    for (SamlProviderConfig config : page.iterateAll()) {
      if (providerIds.contains(config.getProviderId())) {
        collected.incrementAndGet();
        assertEquals("IDP_ENTITY_ID", config.getIdpEntityId());
        assertEquals("https://example.com/login", config.getSsoUrl());
        assertEquals(ImmutableList.of("certificate"), config.getX509Certificates());
        assertEquals("RP_ENTITY_ID", config.getRpEntityId());
        assertEquals("https://projectId.firebaseapp.com/__/auth/handler", config.getCallbackUrl());
      }
    }
    assertEquals(providerIds.size(), collected.get());
  }

  private String signInWithCustomToken(
      String customToken, @Nullable String tenantId) throws IOException {
    final GenericUrl url = new GenericUrl(VERIFY_CUSTOM_TOKEN_URL + "?key="
        + IntegrationTestUtils.getApiKey());
    ImmutableMap.Builder<String, Object> content = ImmutableMap.builder();
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
}
