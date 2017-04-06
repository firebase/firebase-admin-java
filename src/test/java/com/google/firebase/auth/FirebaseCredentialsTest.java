package com.google.firebase.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential.Builder;
import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.FirebaseCredentials.BaseCredential;
import com.google.firebase.auth.FirebaseCredentials.Clock;
import com.google.firebase.auth.FirebaseCredentials.FirebaseAccessToken;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link FirebaseCredentials}.
 */
public class FirebaseCredentialsTest {

  private static final String NEW_ACCESS_TOKEN = "mocknewaccesstoken";
  private static final String ACCESS_TOKEN = "mockaccesstoken";
  private static final String CLIENT_SECRET = "mockclientsecret";
  private static final String CLIENT_ID = "mockclientid";
  private static final String REFRESH_TOKEN = "mockrefreshtoken";

  @Test
  public void defaultCredentialIsCached() {
    Assert.assertEquals(
        FirebaseCredentials.applicationDefault(), FirebaseCredentials.applicationDefault());
  }

  @Test
  public void defaultCredentialDoesntRefetch() throws Exception {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    File credentialsFile = File.createTempFile("google-test-credentials", "json");
    PrintWriter writer = new PrintWriter(Files.newBufferedWriter(credentialsFile.toPath(), UTF_8));
    writer.print(ServiceAccount.EDITOR.asString());
    writer.close();
    Map<String, String> environmentVariables =
        ImmutableMap.<String, String>builder()
            .put("GOOGLE_APPLICATION_CREDENTIALS", credentialsFile.getAbsolutePath())
            .build();
    TestUtils.setEnvironmentVariables(environmentVariables);

    String token =
        Tasks.await(
            FirebaseCredentials.applicationDefault(transport, Utils.getDefaultJsonFactory())
                .getAccessToken(false));
    Assert.assertEquals(ACCESS_TOKEN, token);

    // We should still be able to fetch the token since the certificate is cached
    credentialsFile.delete();
    token =
        Tasks.await(
            FirebaseCredentials.applicationDefault(transport, Utils.getDefaultJsonFactory())
                .getAccessToken(false));
    Assert.assertNotNull(token);
    Assert.assertEquals(ACCESS_TOKEN, token);
  }

  @Test
  public void canResolveTokenMoreThanOnce() throws ExecutionException, InterruptedException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(
            ServiceAccount.EDITOR.asStream(), transport, Utils.getDefaultJsonFactory());

    Tasks.await(credential.getAccessToken(false));
    Tasks.await(credential.getAccessToken(false));
  }

  @Test
  public void certificateReadIsDoneSynchronously()
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(
            ServiceAccount.EDITOR.asString().getBytes(Charset.defaultCharset()));

    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());

    Assert.assertEquals(0, inputStream.available());
    inputStream.close();

    Tasks.await(credential.getAccessToken(false));
    Tasks.await(credential.getAccessToken(true));
  }

  @Test
  public void certificateReadChecksForProjectId()
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    String accountWithoutProjectId =
        ServiceAccount.EDITOR.asString().replace("project_id", "missing");
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(accountWithoutProjectId.getBytes(Charset.defaultCharset()));
    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());

    try {
      Tasks.await(credential.getAccessToken(false));
      Assert.fail();
    } catch (Exception e) {
      Assert.assertEquals(
          "org.json.JSONException: Failed to parse service account: 'project_id' must be set",
          e.getMessage());
      Assert.assertTrue(e.getCause() instanceof JSONException);
    }
  }

  @Test
  public void certificateReadThrowsRuntimeException()
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    InputStream inputStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Expected");
          }
        };

    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());

    try {
      Tasks.await(credential.getAccessToken(false));
      Assert.fail();
    } catch (Exception e) {
      Assert.assertEquals("java.io.IOException: Failed to read service account", e.getMessage());
      Assert.assertEquals("Expected", e.getCause().getCause().getMessage());
    }
  }

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void nullThrowsRuntimeExceptionFromCertificate()
      throws ExecutionException, InterruptedException, IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);
    thrown.expect(NullPointerException.class);
    FirebaseCredentials.fromCertificate(null, transport, Utils.getDefaultJsonFactory());
  }

  @Test
  public void nullThrowsRuntimeExceptionFromRefreshToken()
      throws ExecutionException, InterruptedException, IOException {
    final MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);
    thrown.expect(NullPointerException.class);
    FirebaseCredentials.fromRefreshToken(null, transport, Utils.getDefaultJsonFactory());
  }

  @Test
  public void refreshTokenReadIsDoneSynchronously()
      throws ExecutionException, InterruptedException, IOException, JSONException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);

    JSONObject secretJson = new JSONObject();
    secretJson.put("client_id", CLIENT_ID);
    secretJson.put("client_secret", CLIENT_SECRET);
    secretJson.put("refresh_token", REFRESH_TOKEN);
    secretJson.put("type", "authorized_user");
    InputStream inputStream = new ByteArrayInputStream(secretJson.toString(0).getBytes("UTF-8"));

    FirebaseCredential credential =
        FirebaseCredentials.fromRefreshToken(inputStream, transport, Utils.getDefaultJsonFactory());

    Assert.assertEquals(0, inputStream.available());
    inputStream.close();

    Tasks.await(credential.getAccessToken(false));
    Tasks.await(credential.getAccessToken(true));
  }

  @Test
  public void refreshTokenReadThrowsRuntimeException()
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    InputStream inputStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Expected");
          }
        };

    FirebaseCredential credential =
        FirebaseCredentials.fromRefreshToken(inputStream, transport, Utils.getDefaultJsonFactory());

    try {
      Tasks.await(credential.getAccessToken(false));
      Assert.fail();
    } catch (Exception e) {
      Assert.assertEquals("java.io.IOException: Failed to read refresh token", e.getMessage());
      Assert.assertEquals("Expected", e.getCause().getCause().getMessage());
    }
  }

  @Test
  public void forceRefreshWorks() throws ExecutionException, InterruptedException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(
            ServiceAccount.EDITOR.asStream(), transport, Utils.getDefaultJsonFactory());

    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));

    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), NEW_ACCESS_TOKEN);
    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));
    Assert.assertEquals(NEW_ACCESS_TOKEN, Tasks.await(credential.getAccessToken(true)));
  }

  @Test
  public void tokenExpirationWorks() throws Exception {
    final TestClock clock = new TestClock();
    final GoogleCredential googleCredential = new MockGoogleCredential(new Builder());
    googleCredential.setAccessToken(ACCESS_TOKEN);
    googleCredential.setExpirationTimeMilliseconds(10L);
    TestCredential credential = new TestCredential(clock, googleCredential);

    for (long i = 0; i < 10; i++) {
      clock.timestamp = i;
      Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));
      Assert.assertEquals(1, credential.getFetchCount());
    }

    clock.timestamp = 11;
    googleCredential.setExpirationTimeMilliseconds(20L);
    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));
    Assert.assertEquals(2, credential.getFetchCount());

    for (long i = 10; i < 20; i++) {
      clock.timestamp = i;
      Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));
      Assert.assertEquals(2, credential.getFetchCount());
    }

    clock.timestamp = 21;
    googleCredential.setExpirationTimeMilliseconds(30L);
    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken(false)));
    Assert.assertEquals(3, credential.getFetchCount());
  }

  private static class TestCredential extends BaseCredential {

    private final AtomicInteger fetchCount = new AtomicInteger(0);
    private final GoogleCredential googleCredential;

    TestCredential(Clock clock, GoogleCredential googleCredential) {
      super(new MockTokenServerTransport(), Utils.getDefaultJsonFactory(), clock);
      this.googleCredential = googleCredential;
    }

    @Override
    GoogleCredential fetchCredential() throws Exception {
      return googleCredential;
    }

    @Override
    FirebaseAccessToken fetchToken(GoogleCredential credential) throws Exception {
      try {
        return new FirebaseAccessToken(credential, clock);
      } finally {
        fetchCount.incrementAndGet();
      }
    }

    int getFetchCount() {
      return fetchCount.get();
    }
  }

  private static class TestClock extends Clock {

    long timestamp = 0;

    @Override
    protected long now() {
      return this.timestamp;
    }
  }
}
