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

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.User.NewAccount;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseAuthIT {

  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
  }

  @Test
  public void testCreateUser() throws Exception {
    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    NewAccount account = new NewAccount();
    String uid = Tasks.await(um.createUser(account));

    User user = Tasks.await(um.getUser(uid));
    assertEquals(uid, user.getUid());
    assertFalse(user.isEmailVerified());
  }

  @Test
  public void testCreateUserWithMetadata() throws Exception {

    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    String expected = UUID.randomUUID().toString().replaceAll("-", "");
    String email =
        "test" + expected.substring(0, 12) + "@example." + expected.substring(12) + ".com";
    NewAccount account = new NewAccount()
        .setUid(expected)
        .setDisplayName("Test User")
        .setEmail(email)
        .setPhotoUrl("https://example.com/photo.png")
        .setPassword("secret")
        .setEmailVerified(true);
    String uid = Tasks.await(um.createUser(account));
    assertEquals(expected, uid);

    User user = Tasks.await(um.getUser(expected));
    assertEquals("Test User", user.getDisplayName());
    assertEquals(email, user.getEmail());
    assertEquals("https://example.com/photo.png", user.getPhotoUrl());
    assertTrue(user.isEmailVerified());
  }

  @Test
  public void testGetUser() throws Exception {
    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    String uid = "3kmgpFRHUdQEBFdwrSYx";
    User user = Tasks.await(um.getUser(uid));
    assertEquals(uid, user.getUid());
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
  public void testGetUserByEmail() throws Exception {
    FirebaseUserManager um = FirebaseAuth.getInstance(masterApp).getUserManager();
    String email = "3kmgpFRHUdQEBFdwrSYx@example.com".toLowerCase();
    User user = Tasks.await(um.getUserByEmail(email));
    assertEquals(email, user.getEmail());
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

}
