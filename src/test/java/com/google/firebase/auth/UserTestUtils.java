/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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
import static org.junit.Assert.fail;

import com.google.firebase.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.rules.ExternalResource;

public final class UserTestUtils {

  public static void assertUserDoesNotExist(AbstractFirebaseAuth firebaseAuth, String uid)
      throws Exception {
    try {
      firebaseAuth.getUserAsync(uid).get();
      fail("No error thrown for getting a user which was expected to be absent.");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(ErrorCode.NOT_FOUND,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  public static RandomUser generateRandomUserInfo() {
    String uid = UUID.randomUUID().toString().replaceAll("-", "");
    String email = String.format(
        "test%s@example.%s.com",
        uid.substring(0, 12),
        uid.substring(12)).toLowerCase();
    return new RandomUser(uid, email, generateRandomPhoneNumber());
  }

  private static String generateRandomPhoneNumber() {
    Random random = new Random();
    StringBuilder builder = new StringBuilder("+1");
    for (int i = 0; i < 10; i++) {
      builder.append(random.nextInt(10));
    }
    return builder.toString();
  }

  public static class RandomUser {
    private final String uid;
    private final String email;
    private final String phoneNumber;

    private RandomUser(String uid, String email, String phoneNumber) {
      this.uid = uid;
      this.email = email;
      this.phoneNumber = phoneNumber;
    }

    public String getUid() {
      return uid;
    }

    public String getEmail() {
      return email;
    }

    public String getPhoneNumber() {
      return phoneNumber;
    }
  }

  /**
   * Creates temporary Firebase user accounts for testing, and deletes them at the end of each
   * test case.
   */
  public static final class TemporaryUser extends ExternalResource {

    private final AbstractFirebaseAuth auth;
    private final List<String> users = new ArrayList<>();

    public TemporaryUser(AbstractFirebaseAuth auth) {
      this.auth = auth;
    }

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

