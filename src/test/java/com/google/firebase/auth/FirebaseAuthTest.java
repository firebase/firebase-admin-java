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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.core.ApiFuture;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.base.Defaults;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseCustomAuthToken;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FirebaseAuthTest {

  private static final String ACCESS_TOKEN = "mockaccesstoken";
  private static final String CLIENT_SECRET = "mockclientsecret";
  private static final String CLIENT_ID = "mockclientid";
  private static final String REFRESH_TOKEN = "mockrefreshtoken";
  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  private final FirebaseOptions firebaseOptions;
  private final boolean isCertCredential;

  public FirebaseAuthTest(FirebaseOptions baseOptions, boolean isCertCredential) {
    this.firebaseOptions = baseOptions;
    this.isCertCredential = isCertCredential;
  }

  @Parameters
  public static Collection<Object[]> data() throws Exception {
    // Initialize this test suite with all available credential implementations.
    return Arrays.asList(
        new Object[][] {
          {
            new FirebaseOptions.Builder().setCredentials(createCertificateCredential()).build(),
            /* isCertCredential */ true
          },
          {
            new FirebaseOptions.Builder().setCredentials(createRefreshTokenCredential()).build(),
            /* isCertCredential */ false
          },
          {
            new FirebaseOptions.Builder()
                .setCredentials(TestUtils.getApplicationDefaultCredentials())
                .build(),
            /* isCertCredential */ false
          },
          {
            new FirebaseOptions.Builder().setCredential(
                createFirebaseCertificateCredential()).build(),
            /* isCertCredential */ true
          },
          {
            new FirebaseOptions.Builder().setCredential(
                createFirebaseRefreshTokenCredential()).build(),
            /* isCertCredential */ false
          },
        });
  }

  private static GoogleCredentials createApplicationDefaultCredential() throws IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    // Set the GOOGLE_APPLICATION_CREDENTIALS environment variable for application-default
    // credentials. This requires us to write the credentials to the location specified by the
    // environment variable.
    File credentialsFile = File.createTempFile("google-test-credentials", "json");
    PrintWriter writer = new PrintWriter(Files.newBufferedWriter(credentialsFile.toPath(), UTF_8));
    writer.print(ServiceAccount.EDITOR.asString());
    writer.close();
    Map<String, String> environmentVariables =
        ImmutableMap.<String, String>builder()
            .put("GOOGLE_APPLICATION_CREDENTIALS", credentialsFile.getAbsolutePath())
            .build();
    TestUtils.setEnvironmentVariables(environmentVariables);
    credentialsFile.deleteOnExit();

    return GoogleCredentials.getApplicationDefault(new HttpTransportFactory() {
      @Override
      public HttpTransport create() {
        return transport;
      }
    });
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

  private static FirebaseCredential createFirebaseRefreshTokenCredential()
      throws IOException {

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
    return FirebaseCredentials.fromRefreshToken(refreshTokenStream, transport, JSON_FACTORY);
  }

  private static GoogleCredentials createCertificateCredential() throws IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);
    return ServiceAccountCredentials.fromStream(ServiceAccount.EDITOR.asStream(),
        new HttpTransportFactory() {
          @Override
          public HttpTransport create() {
            return transport;
          }
        });
  }

  private static FirebaseCredential createFirebaseCertificateCredential() throws IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);
    return FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream(),
        transport, JSON_FACTORY);
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
  public void testGetInstance() throws ExecutionException, InterruptedException {
    FirebaseAuth defaultAuth = FirebaseAuth.getInstance();
    assertNotNull(defaultAuth);
    assertSame(defaultAuth, FirebaseAuth.getInstance());
    String token = TestOnlyImplFirebaseTrampolines.getToken(FirebaseApp.getInstance(), false);
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testGetInstanceForApp() throws ExecutionException, InterruptedException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    assertSame(auth, FirebaseAuth.getInstance(app));
    String token = TestOnlyImplFirebaseTrampolines.getToken(app, false);
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testAppDelete() throws ExecutionException, InterruptedException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();
    try {
      FirebaseAuth.getInstance(app);
      fail("No error thrown when getting auth instance after deleting app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }

  @Test
  public void testInvokeAfterAppDelete() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInvokeAfterAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();

    for (Method method : auth.getClass().getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
        continue;
      }

      List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
      for (Class<?> parameterType : method.getParameterTypes()) {
        parameters.add(Defaults.defaultValue(parameterType));
      }
      try {
        method.invoke(auth, parameters.toArray());
        fail("No error thrown when invoking auth after deleting app; method: " + method.getName());
      } catch (InvocationTargetException expected) {
        String message = "FirebaseAuth instance is no longer alive. This happens when "
            + "the parent FirebaseApp instance has been deleted.";
        Throwable cause = expected.getCause();
        assertTrue(cause instanceof IllegalStateException);
        assertEquals(message, cause.getMessage());
      }
    }
  }

  @Test
  public void testInitAfterAppDelete() throws ExecutionException, InterruptedException,
      TimeoutException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth1 = FirebaseAuth.getInstance(app);
    assertNotNull(auth1);
    app.delete();

    app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth2 = FirebaseAuth.getInstance(app);
    assertNotNull(auth2);
    assertNotSame(auth1, auth2);

    if (isCertCredential) {
      ApiFuture<String> future = auth2.createCustomTokenAsync("foo");
      assertNotNull(future);
      assertNotNull(future.get(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  public void testAppWithAuthVariableOverrides() throws ExecutionException, InterruptedException {
    Map<String, Object> authVariableOverrides = Collections.singletonMap("uid", (Object) "uid1");
    FirebaseOptions options =
        new FirebaseOptions.Builder(firebaseOptions)
            .setDatabaseAuthVariableOverride(authVariableOverrides)
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testGetAppWithUid");
    assertEquals("uid1", app.getOptions().getDatabaseAuthVariableOverride().get("uid"));
    String token = TestOnlyImplFirebaseTrampolines.getToken(app, false);
    Assert.assertTrue(!token.isEmpty());
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
          "Must initialize FirebaseApp with a service account credential to call "
              + "createCustomToken()",
          expected.getMessage());
    }
  }

  @Test
  public void testProjectIdRequired() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testProjectIdRequired");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    Assume.assumeTrue("Skipping testProjectIdRequired for settings with project ID",
        Strings.isNullOrEmpty(projectId));

    try {
      FirebaseAuth.getInstance(app).verifyIdTokenAsync("foo").get();
      fail("Expected exception.");
    } catch (IllegalStateException expected) {
      Assert.assertEquals(
          "Must initialize FirebaseApp with a project ID to call verifyIdToken()",
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

  @Test(expected = IllegalArgumentException.class)
  public void testAuthExceptionNullErrorCode() {
    new FirebaseAuthException(null, "test");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAuthExceptionEmptyErrorCode() {
    new FirebaseAuthException("", "test");
  }
}
