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

  private static FirebaseApp masterApp;
  private static FirebaseUserManager userManager;

  @BeforeClass
  public static void setUpClass() throws Exception {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
    userManager = FirebaseAuth.getInstance(masterApp).getUserManager();
  }

  @Test
  public void testGetNonExistingUser() throws Exception {
    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    try {
      Tasks.await(um.getUser("non.existing"));
      fail("No error thrown for non existing uid");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
    }
  }

  @Test
  public void testGetNonExistingUserByEmail() throws Exception {
    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    try {
      Tasks.await(um.getUserByEmail("non.existing@definitely.non.existing"));
      fail("No error thrown for non existing email");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
    }
  }

  @Test
  public void testUserLifecycle() throws Exception {
    // Create user
    String uid = Tasks.await(userManager.createUser(User.newBuilder()));

    // Get user
    User user = Tasks.await(userManager.getUser(uid));
    assertEquals(uid, user.getUid());
    assertNull(user.getDisplayName());
    assertNull(user.getEmail());
    assertNull(user.getPhotoUrl());
    assertFalse(user.isEmailVerified());
    assertFalse(user.isDisabled());

    // Update user
    String randomId = UUID.randomUUID().toString().replaceAll("-", "");
    String userEmail = ("test" + randomId.substring(0, 12) + "@example." + randomId.substring(12)
        + ".com").toLowerCase();
    User.Updater updater = user.updater()
        .setDisplayName("Updated Name")
        .setEmail(userEmail)
        .setPhotoUrl("https://example.com/photo.png")
        .setEmailVerified(true)
        .setPassword("secret");
    user = Tasks.await(userManager.updateUser(updater));
    assertEquals(uid, user.getUid());
    assertEquals("Updated Name", user.getDisplayName());
    assertEquals(userEmail, user.getEmail());
    assertEquals("https://example.com/photo.png", user.getPhotoUrl());
    assertTrue(user.isEmailVerified());
    assertFalse(user.isDisabled());

    // Disable user and remove properties
    updater = user.updater()
        .setPhotoUrl(null)
        .setDisplayName(null)
        .setDisabled(true);
    user = Tasks.await(userManager.updateUser(updater));
    assertEquals(uid, user.getUid());
    assertNull(user.getDisplayName());
    assertEquals(userEmail, user.getEmail());
    assertNull(user.getPhotoUrl());
    assertTrue(user.isEmailVerified());
    assertTrue(user.isDisabled());
  }

}
