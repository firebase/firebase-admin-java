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

import com.google.firebase.FirebaseApp;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseAuthIT {

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
      Tasks.await(auth.updateUser(UserRecord.updater("non.existing")));
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

  @Test
  public void testCreateUserWithParams() throws Exception {
    String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    String userEmail = ("test" + randomId.substring(0, 12) + "@example." + randomId.substring(12)
        + ".com").toLowerCase();
    UserRecord.Builder builder = UserRecord.builder()
        .setUid(randomId)
        .setEmail(userEmail)
        .setDisplayName("Random User")
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("password");

    UserRecord userRecord = Tasks.await(auth.createUser(builder));
    try {
      assertEquals(randomId, userRecord.getUid());
      assertEquals("Random User", userRecord.getDisplayName());
      assertEquals(userEmail, userRecord.getEmail());
      assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
      assertTrue(userRecord.isEmailVerified());
      assertFalse(userRecord.isDisabled());

      checkRecreate(randomId);
    } finally {
      Tasks.await(auth.deleteUser(userRecord.getUid()));
    }
  }

  private void checkRecreate(String uid) throws Exception {
    try {
      Tasks.await(auth.createUser(UserRecord.builder().setUid(uid)));
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
    UserRecord userRecord = Tasks.await(auth.createUser(UserRecord.builder()));
    String uid = userRecord.getUid();

    // Get user
    userRecord = Tasks.await(auth.getUser(userRecord.getUid()));
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getDisplayName());
    assertNull(userRecord.getEmail());
    assertNull(userRecord.getPhotoUrl());
    assertFalse(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());
    assertTrue(userRecord.getUserMetadata().getCreationTimestamp() > 0);
    assertEquals(0, userRecord.getUserMetadata().getLastSignInTimestamp());

    // Update user
    String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    String userEmail = ("test" + randomId.substring(0, 12) + "@example." + randomId.substring(12)
        + ".com").toLowerCase();
    UserRecord.Updater updater = userRecord.updater()
        .setDisplayName("Updated Name")
        .setEmail(userEmail)
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("secret");
    userRecord = Tasks.await(auth.updateUser(updater));
    assertEquals(uid, userRecord.getUid());
    assertEquals("Updated Name", userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertEquals("https://example.com/photo.png", userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertFalse(userRecord.isDisabled());

    // Disable user and remove properties
    updater = userRecord.updater()
        .setPhotoUrl(null)
        .setDisplayName(null)
        .setDisabled(true);
    userRecord = Tasks.await(auth.updateUser(updater));
    assertEquals(uid, userRecord.getUid());
    assertNull(userRecord.getDisplayName());
    assertEquals(userEmail, userRecord.getEmail());
    assertNull(userRecord.getPhotoUrl());
    assertTrue(userRecord.isEmailVerified());
    assertTrue(userRecord.isDisabled());

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

}
