/*
 * Copyright 2020 Google Inc.
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
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.UserTestUtils.RandomUser;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetUsersIT {
  private static FirebaseAuth auth;
  private static UserRecord testUser1;
  private static UserRecord testUser2;
  private static UserRecord testUser3;
  private static String importUserUid;

  @BeforeClass
  public static void setUpClass() throws Exception {
    FirebaseApp masterApp = IntegrationTestUtils.ensureDefaultApp();
    auth = FirebaseAuth.getInstance(masterApp);

    testUser1 = FirebaseAuthIT.newUserWithParams(auth);
    testUser2 = FirebaseAuthIT.newUserWithParams(auth);
    testUser3 = FirebaseAuthIT.newUserWithParams(auth);

    RandomUser randomUser = UserTestUtils.generateRandomUserInfo();
    importUserUid = randomUser.getUid();
    UserImportResult result = auth.importUsers(ImmutableList.of(
          ImportUserRecord.builder()
          .setUid(randomUser.getUid())
          .setEmail(randomUser.getEmail())
          .setPhoneNumber(randomUser.getPhoneNumber())
          .addUserProvider(
              UserProvider.builder()
                  .setProviderId("google.com")
                  .setUid("google_" + randomUser.getUid())
                  .build())
          .build()
          ));
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getFailureCount());
  }

  @AfterClass
  public static void cleanup() throws Exception {
    // TODO(rsgowman): deleteUsers (plural) would make more sense here, but it's currently rate
    // limited to 1qps. When/if that's relaxed, change this to just delete them all at once.
    auth.deleteUser(testUser1.getUid());
    auth.deleteUser(testUser2.getUid());
    auth.deleteUser(testUser3.getUid());
    auth.deleteUser(importUserUid);
  }

  @Test
  public void testVariousIdentifiers() throws Exception {
    GetUsersResult result = auth.getUsersAsync(ImmutableList.<UserIdentifier>of(
          new UidIdentifier(testUser1.getUid()),
          new EmailIdentifier(testUser2.getEmail()),
          new PhoneIdentifier(testUser3.getPhoneNumber()),
          new ProviderIdentifier("google.com", "google_" + importUserUid)
          )).get();

    Collection<String> expectedUids = ImmutableList.of(
        testUser1.getUid(), testUser2.getUid(), testUser3.getUid(), importUserUid);

    assertTrue(sameUsers(result.getUsers(), expectedUids));
    assertEquals(0, result.getNotFound().size());
  }

  @Test
  public void testIgnoresNonExistingUsers() throws Exception {
    UidIdentifier doesntExistId = new UidIdentifier("uid_that_doesnt_exist");
    GetUsersResult result = auth.getUsersAsync(ImmutableList.<UserIdentifier>of(
          new UidIdentifier(testUser1.getUid()),
          doesntExistId,
          new UidIdentifier(testUser3.getUid())
          )).get();

    Collection<String> expectedUids = ImmutableList.of(testUser1.getUid(), testUser3.getUid());

    assertTrue(sameUsers(result.getUsers(), expectedUids));
    assertEquals(1, result.getNotFound().size());
    assertTrue(result.getNotFound().contains(doesntExistId));
  }

  @Test
  public void testOnlyNonExistingUsers() throws Exception {
    UidIdentifier doesntExistId = new UidIdentifier("uid_that_doesnt_exist");
    GetUsersResult result = auth.getUsersAsync(ImmutableList.<UserIdentifier>of(
          doesntExistId
          )).get();

    assertEquals(0, result.getUsers().size());
    assertEquals(1, result.getNotFound().size());
    assertTrue(result.getNotFound().contains(doesntExistId));
  }

  @Test
  public void testDedupsDuplicateUsers() throws Exception {
    GetUsersResult result = auth.getUsersAsync(ImmutableList.<UserIdentifier>of(
          new UidIdentifier(testUser1.getUid()),
          new UidIdentifier(testUser1.getUid())
          )).get();

    Collection<String> expectedUids = ImmutableList.of(testUser1.getUid());

    assertEquals(1, result.getUsers().size());
    assertTrue(sameUsers(result.getUsers(), expectedUids));
    assertEquals(0, result.getNotFound().size());
  }

  /**
   * Checks to see if the userRecords collection contains the given uids.
   *
   * <p>Behaviour is undefined if there are duplicate entries in either of the parameters.
   */
  private boolean sameUsers(Collection<UserRecord> userRecords, Collection<String> uids) {
    if (userRecords.size() != uids.size()) {
      return false;
    }

    for (UserRecord userRecord : userRecords) {
      if (!uids.contains(userRecord.getUid())) {
        return false;
      }
    }

    return true;
  }
}
