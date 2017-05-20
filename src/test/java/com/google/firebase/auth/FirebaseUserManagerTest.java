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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.firebase.auth.FirebaseUserManager.TokenSource;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.TestUtils;
import org.junit.Test;

public class FirebaseUserManagerTest {

  private static final GsonFactory gson = new GsonFactory();

  @Test
  public void testGetUser() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("user.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(new TestTokenSource(), gson, transport);
    User user = Tasks.await(userManager.getUser("testuser"));
    checkUser(user);
  }

  @Test
  public void testGetUserByEmail() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent(TestUtils.loadResource("user.json"));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    FirebaseUserManager userManager = new FirebaseUserManager(new TestTokenSource(), gson, transport);
    User user = Tasks.await(userManager.getUserByEmail("testuser@example.com"));
    checkUser(user);
  }

  private void checkUser(User user) {
    assertEquals("testuser", user.getUid());
    assertEquals("testuser@example.com", user.getEmail());
    assertEquals("Test User", user.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", user.getPhotoUrl());
    assertEquals(1234567890, user.getCreatedAt());
    assertEquals(1, user.getProviders().length);
    assertFalse(user.isDisabled());
    assertEquals(0, user.getLastLoginAt());
    assertTrue(user.isEmailVerified());

    Provider provider = user.getProviders()[0];
    assertEquals("testuser@example.com", provider.getUid());
    assertEquals("testuser@example.com", provider.getEmail());
    assertEquals("Test User", provider.getDisplayName());
    assertEquals("http://www.example.com/testuser/photo.png", provider.getPhotoUrl());
    assertEquals("password", provider.getProviderId());
  }

  private static class TestTokenSource implements TokenSource {
    @Override
    public Task<GetTokenResult> getToken() {
      return Tasks.forResult(new GetTokenResult("mock-token"));
    }
  }

}
