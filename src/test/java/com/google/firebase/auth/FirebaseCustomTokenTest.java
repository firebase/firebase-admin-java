/*
 * Copyright 2018 Google Inc.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseCustomAuthToken;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class FirebaseCustomTokenTest {

  @After
  public void cleanup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testCreateCustomToken() throws Exception {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(ServiceAccountCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token = auth.createCustomTokenAsync("user1").get();
    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getIssuer(), ServiceAccount.EDITOR.getEmail());
    assertNull(parsedToken.getPayload().getDeveloperClaims());
    assertTrue(ServiceAccount.EDITOR.verifySignature(parsedToken));
  }

  @Test
  public void testCreateCustomTokenWithDeveloperClaims() throws Exception {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(ServiceAccountCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token = auth.createCustomTokenAsync(
        "user1", MapBuilder.of("claim", "value")).get();
    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getIssuer(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getDeveloperClaims().keySet().size(), 1);
    assertEquals(parsedToken.getPayload().getDeveloperClaims().get("claim"), "value");
    assertTrue(ServiceAccount.EDITOR.verifySignature(parsedToken));
  }

  @Test
  public void testCreateCustomTokenWithoutServiceAccountCredentials() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    String content = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signedBlob", BaseEncoding.base64().encode("test-signature".getBytes())));
    response.setContent(content);
    MockHttpTransport transport = new MultiRequestMockHttpTransport(ImmutableList.of(response));

    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project-id")
        .setServiceAccountId("test@service.account")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token = auth.createCustomTokenAsync("user1").get();
    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), "test@service.account");
    assertEquals(parsedToken.getPayload().getIssuer(), "test@service.account");
    assertNull(parsedToken.getPayload().getDeveloperClaims());
    assertEquals("test-signature", new String(parsedToken.getSignatureBytes()));
  }

  @Test
  public void testCreateCustomTokenWithDiscoveredServiceAccount() throws Exception {
    String content = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signedBlob", BaseEncoding.base64().encode("test-signature".getBytes())));
    List<MockLowLevelHttpResponse> responses = ImmutableList.of(
        // Service account discovery response
        new MockLowLevelHttpResponse().setContent("test@service.account"),

        // Sign blob response
        new MockLowLevelHttpResponse().setContent(content)
    );
    MockHttpTransport transport = new MultiRequestMockHttpTransport(responses);

    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project-id")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token = auth.createCustomTokenAsync("user1").get();
    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), "test@service.account");
    assertEquals(parsedToken.getPayload().getIssuer(), "test@service.account");
    assertNull(parsedToken.getPayload().getDeveloperClaims());
    assertEquals("test-signature", new String(parsedToken.getSignatureBytes()));
  }

  @Test
  public void testNoServiceAccount() throws Exception {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project-id")
        .setHttpTransport(new HttpTransport() {
          @Override
          protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            throw new IOException("transport error");
          }
        })
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    try {
      FirebaseAuth.getInstance(app).createCustomTokenAsync("foo").get();
      fail("Expected exception.");
    } catch (IllegalStateException expected) {
      Assert.assertEquals(
          "Failed to initialize FirebaseTokenFactory. Make sure to initialize the SDK with "
              + "service account credentials or specify a service account ID with "
              + "iam.serviceAccounts.signBlob permission. Please refer to "
              + "https://firebase.google.com/docs/auth/admin/create-custom-tokens for more details "
              + "on creating custom tokens.",
          expected.getMessage());
    }
  }
}
