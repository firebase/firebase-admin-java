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

import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseCustomAuthToken;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FirebaseCustomTokenTest {

  private static final String ACCESS_TOKEN = "mockaccesstoken";
  private static final String CLIENT_SECRET = "mockclientsecret";
  private static final String CLIENT_ID = "mockclientid";
  private static final String REFRESH_TOKEN = "mockrefreshtoken";
  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  private final FirebaseOptions firebaseOptions;

  public FirebaseCustomTokenTest(FirebaseOptions baseOptions) {
    this.firebaseOptions = baseOptions;
  }

  @Parameters
  public static Collection<Object[]> data() throws Exception {
    // Initialize this test suite with all available credential implementations.
    return Arrays.asList(
        new Object[][] {
            {
                new FirebaseOptions.Builder().setCredentials(createCertificateCredential()).build(),
            },
            {
                new FirebaseOptions.Builder()
                    .setCredentials(createRefreshTokenCredential())
                    .setProjectId("test-project-id")
                    .build(),
            },
            {
                new FirebaseOptions.Builder()
                    .setCredentials(TestUtils.getApplicationDefaultCredentials())
                    .build(),
            },
        });
  }

  @Before
  public void setup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.initializeApp(firebaseOptions);
  }

  @After
  public void cleanup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testCreateCustomToken() throws Exception {
    GoogleCredentials credentials = TestOnlyImplFirebaseTrampolines.getCredentials(firebaseOptions);
    Assume.assumeTrue("Skipping testCredentialCertificateRequired for cert credential",
        credentials instanceof ServiceAccountCredentials);

    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testCreateCustomToken");
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
    GoogleCredentials credentials = TestOnlyImplFirebaseTrampolines.getCredentials(firebaseOptions);
    Assume.assumeTrue("Skipping testCredentialCertificateRequired for cert credential",
        credentials instanceof ServiceAccountCredentials);

    FirebaseApp app =
        FirebaseApp.initializeApp(firebaseOptions, "testCreateCustomTokenWithDeveloperClaims");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token =
        auth.createCustomTokenAsync("user1", MapBuilder.of("claim", "value")).get();

    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getIssuer(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getDeveloperClaims().keySet().size(), 1);
    assertEquals(parsedToken.getPayload().getDeveloperClaims().get("claim"), "value");
    assertTrue(ServiceAccount.EDITOR.verifySignature(parsedToken));
  }

  @Test
  public void testServiceAccountRequired() throws Exception {
    GoogleCredentials credentials = TestOnlyImplFirebaseTrampolines.getCredentials(firebaseOptions);
    Assume.assumeFalse("Skipping testServiceAccountRequired for service account credentials",
        credentials instanceof ServiceAccountCredentials);

    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testServiceAccountRequired");
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

  @Test
  public void testVerifyIdTokenWithExplicitProjectId() throws Exception {
    GoogleCredentials credentials = TestOnlyImplFirebaseTrampolines.getCredentials(firebaseOptions);
    Assume.assumeFalse(
        "Skipping testVerifyIdTokenWithExplicitProjectId for service account credentials",
        credentials instanceof ServiceAccountCredentials);

    FirebaseOptions options =
        new FirebaseOptions.Builder(firebaseOptions)
            .setProjectId("mock-project-id")
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testVerifyIdTokenWithExplicitProjectId");
    try {
      FirebaseAuth.getInstance(app).verifyIdTokenAsync("foo").get();
      fail("Expected exception.");
    } catch (ExecutionException expected) {
      Assert.assertNotEquals(
          "com.google.firebase.FirebaseException: Must initialize FirebaseApp with a project ID "
              + "to call verifyIdToken()",
          expected.getMessage());
      assertTrue(expected.getCause() instanceof IllegalArgumentException);
    }
  }

  private static GoogleCredentials createRefreshTokenCredential() throws IOException {

    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);

    Map<String, Object> secretJson = new HashMap<>();
    secretJson.put("client_id", CLIENT_ID);
    secretJson.put("client_secret", CLIENT_SECRET);
    secretJson.put("refresh_token", REFRESH_TOKEN);
    secretJson.put("type", "authorized_user");
    InputStream refreshTokenStream =
        new ByteArrayInputStream(JSON_FACTORY.toByteArray(secretJson));

    return UserCredentials.fromStream(refreshTokenStream, new HttpTransportFactory() {
      @Override
      public HttpTransport create() {
        return transport;
      }
    });
  }

  private static GoogleCredentials createCertificateCredential() throws IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport(
        "https://accounts.google.com/o/oauth2/token");
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);
    return ServiceAccountCredentials.fromStream(ServiceAccount.EDITOR.asStream(),
        new HttpTransportFactory() {
          @Override
          public HttpTransport create() {
            return transport;
          }
        });
  }

}
