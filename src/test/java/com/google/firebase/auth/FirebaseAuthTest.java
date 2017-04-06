package com.google.firebase.auth;

import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.internal.FirebaseCustomAuthToken;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

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
      throws JSONException, UnsupportedEncodingException {

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

  private static FirebaseCredential createCertificateCredential() {
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
    if (!isCertCredential) {
      return;
    }

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
    if (!isCertCredential) {
      return;
    }

    FirebaseApp app =
        FirebaseApp.initializeApp(firebaseOptions, "testCreateCustomTokenWithDeveloperClaims");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);

    String token =
        Tasks.await(auth.createCustomToken("user1", ImmutableMap.of("claim", (Object) "value")));

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
    Assert.assertNotNull(Tasks.await(TestOnlyImplFirebaseTrampolines.getToken(
        app, false)).getToken());
  }

  @Test
  public void testCredentialCertificateRequired() throws Exception {
    if (isCertCredential) {
      return;
    }

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
}
