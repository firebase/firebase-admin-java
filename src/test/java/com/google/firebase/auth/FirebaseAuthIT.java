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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseAuthIT {

  private static final String ID_TOOLKIT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken";
  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
  private static final HttpTransport transport = Utils.getDefaultTransport();

  private static FirebaseAuth auth;

  @BeforeClass
  public static void setUpClass() throws Exception {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    auth = FirebaseAuth.getInstance(masterApp);
  }

  @Test
  public void testGetNonExistingUser() throws Exception {
    try {
      Tasks.await(auth.getUser("non.existing"));
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
      Tasks.await(auth.getUserByEmail("non.existing@definitely.non.existing"));
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
      Tasks.await(auth.updateUser(new UpdateRequest("non.existing")));
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_UPDATE_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testDeleteNonExistingUser() throws Exception {
    try {
      Tasks.await(auth.deleteUser("non.existing"));
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_DELETE_ERROR,
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

    UserRecord userRecord = Tasks.await(auth.createUser(user));
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
      Tasks.await(auth.deleteUser(userRecord.getUid()));
    }
  }

  private void checkRecreate(String uid) throws Exception {
    try {
      Tasks.await(auth.createUser(new CreateRequest().setUid(uid)));
      fail("No error thrown for creating user with existing ID");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_CREATE_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testUserLifecycle() throws Exception {
    // Create user
    UserRecord userRecord = Tasks.await(auth.createUser(new CreateRequest()));
    String uid = userRecord.getUid();

    // Get user
    userRecord = Tasks.await(auth.getUser(userRecord.getUid()));
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
    userRecord = Tasks.await(auth.updateUser(request));
    assertEquals(uid, userRecord.getUid());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertEquals(phone, userRecord.getPhoneNumber());
    assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());
    assertEquals(2, userRecord.getProviderData().length);

    // Get user by email
    userRecord = Tasks.await(auth.getUserByEmail(userRecord.getEmail()));
    assertEquals(uid, userRecord.getUid());

    // Disable user and remove properties
    request = userRecord.updateRequest()
        .setPhotoUrl(null)
        .setDisplayName(null)
        .setPhoneNumber(null)
        .setDisabled(true);
    userRecord = Tasks.await(auth.updateUser(request));
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertNull(userRecord.getPhoneNumber());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());
    assertEquals(1, userRecord.getProviderData().length);

    // Delete user
    Tasks.await(auth.deleteUser(userRecord.getUid()));
    try {
      Tasks.await(auth.getUser(userRecord.getUid()));
      fail("No error thrown for deleted user");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.USER_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  @Test
  public void testCustomToken() throws Exception {
    String customToken = Tasks.await(auth.createCustomToken("user1"));
    String idToken = signInWithCustomToken(customToken);
    FirebaseToken decoded = Tasks.await(auth.verifyIdToken(idToken));
    assertEquals("user1", decoded.getUid());
  }

  @Test
  public void testCustomTokenWithClaims() throws Exception {
    Map<String, Object> devClaims = ImmutableMap.<String, Object>of(
        "premium", true, "subscription", "silver");
    String customToken = Tasks.await(auth.createCustomToken("user2", devClaims));
    String idToken = signInWithCustomToken(customToken);
    FirebaseToken decoded = Tasks.await(auth.verifyIdToken(idToken));
    assertEquals("user2", decoded.getUid());
    assertTrue((Boolean) decoded.getClaims().get("premium"));
    assertEquals("silver", decoded.getClaims().get("subscription"));
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

}
