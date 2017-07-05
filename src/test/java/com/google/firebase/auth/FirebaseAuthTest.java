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
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseCustomAuthToken;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONException;
import org.json.JSONObject;
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
            new FirebaseOptions.Builder().setCredential(createCertificateCredential()).build(),
            /* isCertCredential */ true
          },
          {
            new FirebaseOptions.Builder().setCredential(createRefreshTokenCredential()).build(),
            /* isCertCredential */ false
          },
          {
            new FirebaseOptions.Builder()
                .setCredential(createApplicationDefaultCredential())
                .build(),
            /* isCertCredential */ false
          },
        });
  }

  private static FirebaseCredential createApplicationDefaultCredential() throws IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
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

    return FirebaseCredentials.applicationDefault(transport, Utils.getDefaultJsonFactory());
  }

  private static FirebaseCredential createRefreshTokenCredential()
      throws JSONException, IOException {

    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);

    JSONObject secretJson = new JSONObject();
    secretJson.put("client_id", CLIENT_ID);
    secretJson.put("client_secret", CLIENT_SECRET);
    secretJson.put("refresh_token", REFRESH_TOKEN);
    secretJson.put("type", "authorized_user");
    InputStream refreshTokenStream =
        new ByteArrayInputStream(secretJson.toString(0).getBytes("UTF-8"));

    return FirebaseCredentials.fromRefreshToken(
        refreshTokenStream, transport, Utils.getDefaultJsonFactory());
  }

  private static FirebaseCredential createCertificateCredential() throws IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    return FirebaseCredentials.fromCertificate(
        ServiceAccount.EDITOR.asStream(), transport, Utils.getDefaultJsonFactory());
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
    String token =
        Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(FirebaseApp.getInstance(), false))
            .getToken();
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testGetInstanceForApp() throws ExecutionException, InterruptedException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    assertSame(auth, FirebaseAuth.getInstance(app));
    String token = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(app, false)).getToken();
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
  public void testInvokeAfterAppDelete() throws ExecutionException, InterruptedException {
    Assume.assumeTrue("Skipping testInvokeAfterAppDelete for non-cert credential",
        isCertCredential);
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInvokeAfterAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();
    try {
      auth.createCustomToken("foo");
      fail("No error thrown when invoking auth after deleting app");
    } catch (IllegalStateException expected) {
      // ignore
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
      Task<String> task = auth2.createCustomToken("foo");
      assertNotNull(task);
      assertNotNull(Tasks.await(task, TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
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
    String token = Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(app, false)).getToken();
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testCreateCustomToken() throws Exception {
    Assume.assumeTrue("Skipping testCreateCustomToken for non-cert credential",
        isCertCredential);

    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testCreateCustomToken");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token = Tasks.await(auth.createCustomToken("user1"));

    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getIssuer(), ServiceAccount.EDITOR.getEmail());
    assertNull(parsedToken.getPayload().getDeveloperClaims());
    assertTrue(ServiceAccount.EDITOR.verifySignature(parsedToken));
  }

  @Test
  public void testCreateCustomTokenWithDeveloperClaims() throws Exception {
    Assume.assumeTrue("Skipping testCreateCustomTokenWithDeveloperClaims for non-cert credential",
        isCertCredential);

    FirebaseApp app =
        FirebaseApp.initializeApp(firebaseOptions, "testCreateCustomTokenWithDeveloperClaims");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token =
        Tasks.await(auth.createCustomToken("user1", MapBuilder.of("claim", "value")));

    FirebaseCustomAuthToken parsedToken = FirebaseCustomAuthToken.parse(new GsonFactory(), token);
    assertEquals(parsedToken.getPayload().getUid(), "user1");
    assertEquals(parsedToken.getPayload().getSubject(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getIssuer(), ServiceAccount.EDITOR.getEmail());
    assertEquals(parsedToken.getPayload().getDeveloperClaims().keySet().size(), 1);
    assertEquals(parsedToken.getPayload().getDeveloperClaims().get("claim"), "value");
    assertTrue(ServiceAccount.EDITOR.verifySignature(parsedToken));
  }

  @Test(expected = Exception.class)
  public void testServiceAccountUsedAsRefreshToken() throws Exception {
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromRefreshToken(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testCreateCustomToken");
    Assert.assertNotNull(
        Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(app, false)).getToken());
  }

  @Test
  public void testCredentialCertificateRequired() throws Exception {
    Assume.assumeFalse("Skipping testCredentialCertificateRequired for cert credential",
        isCertCredential);


    FirebaseApp app =
        FirebaseApp.initializeApp(firebaseOptions, "testCredentialCertificateRequired");

    try {
      Tasks.await(FirebaseAuth.getInstance(app).verifyIdToken("foo"));
      fail("Expected exception.");
    } catch (Exception expected) {
      Assert.assertEquals(
          "com.google.firebase.FirebaseException: Must initialize FirebaseApp with a certificate "
              + "credential to call verifyIdToken()",
          expected.getMessage());
    }

    try {
      Tasks.await(FirebaseAuth.getInstance(app).createCustomToken("foo"));
      fail("Expected exception.");
    } catch (Exception expected) {
      Assert.assertEquals(
          "com.google.firebase.FirebaseException: Must initialize FirebaseApp with a certificate "
              + "credential to call createCustomToken()",
          expected.getMessage());
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
