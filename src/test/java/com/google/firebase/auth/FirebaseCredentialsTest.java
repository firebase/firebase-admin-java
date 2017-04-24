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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential.Builder;
import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.FirebaseCredentials.BaseCredential;
import com.google.firebase.auth.GoogleOAuthAccessToken.Clock;
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

  private static final String ACCESS_TOKEN = "mockaccesstoken";
  private static final String CLIENT_SECRET = "mockclientsecret";
  private static final String CLIENT_ID = "mockclientid";
  private static final String REFRESH_TOKEN = "mockrefreshtoken";
  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test(expected = NullPointerException.class)
  public void testNullCertificate() throws IOException {
    FirebaseCredentials.fromCertificate(null);
  }

  @Test(expected = NullPointerException.class)
  public void testNullRefreshToken() throws IOException {
    FirebaseCredentials.fromRefreshToken(null);
  }

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

    GoogleOAuthAccessToken token =
        Tasks.await(
            FirebaseCredentials.applicationDefault(transport, Utils.getDefaultJsonFactory())
                .getAccessToken());
    Assert.assertEquals(ACCESS_TOKEN, token.getAccessToken());

    // We should still be able to fetch the token since the certificate is cached
    Assert.assertTrue(credentialsFile.delete());
    token =
        Tasks.await(
            FirebaseCredentials.applicationDefault(transport, Utils.getDefaultJsonFactory())
                .getAccessToken());
    Assert.assertNotNull(token);
    Assert.assertEquals(ACCESS_TOKEN, token.getAccessToken());
  }

  @Test
  public void canResolveTokenMoreThanOnce()
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    FirebaseCredential credential =
        FirebaseCredentials.fromCertificate(
            ServiceAccount.EDITOR.asStream(), transport, Utils.getDefaultJsonFactory());

    Tasks.await(credential.getAccessToken());
    Tasks.await(credential.getAccessToken());
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

    Tasks.await(credential.getAccessToken());
  }

  @Test
  public void certificateReadChecksForProjectId()
      throws ExecutionException, InterruptedException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    String accountWithoutProjectId =
        ServiceAccount.EDITOR.asString().replace("project_id", "missing");
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(accountWithoutProjectId.getBytes(Charset.defaultCharset()));

    try {
      FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());
      Assert.fail();
    } catch (IOException e) {
      Assert.assertEquals(
          "Failed to parse service account: 'project_id' must be set",
          e.getMessage());
      Assert.assertTrue(e.getCause() instanceof JSONException);
    }
  }

  @Test
  public void certificateReadThrowsRuntimeException()
      throws ExecutionException, InterruptedException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    InputStream inputStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Expected");
          }
        };


    try {
      FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());
      Assert.fail();
    } catch (IOException e) {
      Assert.assertEquals("Expected", e.getMessage());
    }
  }

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

    Tasks.await(credential.getAccessToken());
  }

  @Test
  public void refreshTokenReadThrowsRuntimeException()
      throws ExecutionException, InterruptedException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), ACCESS_TOKEN);

    InputStream inputStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Expected");
          }
        };


    try {
      FirebaseCredentials.fromRefreshToken(inputStream, transport, Utils.getDefaultJsonFactory());
      Assert.fail();
    } catch (IOException e) {
      Assert.assertEquals("Expected", e.getMessage());
    }
  }

  @Test
  public void tokenNotCached() throws Exception {
    final GoogleCredential googleCredential = new MockGoogleCredential(new Builder());
    googleCredential.setAccessToken(ACCESS_TOKEN);
    googleCredential.setExpirationTimeMilliseconds(10L);
    TestCredential credential = new TestCredential(googleCredential);

    for (long i = 0; i < 10; i++) {
      Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken()).getAccessToken());
      Assert.assertEquals(i + 1, credential.getFetchCount());
    }
  }

  @Test
  public void testTokenExpiration() throws Exception {
    final GoogleCredential googleCredential = new MockGoogleCredential(new Builder());
    googleCredential.setAccessToken(ACCESS_TOKEN);
    TestClock clock = new TestClock();
    TestCredential credential = new TestCredential(googleCredential, clock);

    for (long i = 0; i < 10; i++) {
      long expiryTime = (i + 1) * 10L;
      googleCredential.setExpirationTimeMilliseconds(expiryTime);
      GoogleOAuthAccessToken googleToken = Tasks.await(credential.getAccessToken());
      Assert.assertEquals(ACCESS_TOKEN, googleToken.getAccessToken());
      Assert.assertEquals(expiryTime, googleToken.getExpiryTime());

      clock.timestamp = 0L;
      while (clock.timestamp < expiryTime) {
        Assert.assertFalse(googleToken.isExpired());
        clock.advanceClock(1L);
      }

      Assert.assertTrue(googleToken.isExpired());
    }
  }

  private static class TestCredential extends BaseCredential {

    private final AtomicInteger fetchCount = new AtomicInteger(0);
    private final GoogleCredential googleCredential;
    private final Clock clock;

    TestCredential(GoogleCredential googleCredential, Clock clock) {
      super(new MockTokenServerTransport(), Utils.getDefaultJsonFactory());
      this.googleCredential = googleCredential;
      this.clock = clock;
    }

    TestCredential(GoogleCredential googleCredential) {
      this(googleCredential, null);
    }

    @Override
    GoogleCredential fetchCredential() throws IOException {
      return googleCredential;
    }

    @Override
    GoogleOAuthAccessToken fetchToken(GoogleCredential credential) throws IOException {
      try {
        if (clock == null) {
          return FirebaseCredentials.newAccessToken(credential);
        } else {
          return newAccessToken(credential.getAccessToken(),
              credential.getExpirationTimeMilliseconds(), clock);
        }
      } finally {
        fetchCount.incrementAndGet();
      }
    }

    int getFetchCount() {
      return fetchCount.get();
    }
  }

  public static class TestClock extends GoogleOAuthAccessToken.Clock {
    long timestamp = 0;

    @Override
    public long now() {
      return this.timestamp;
    }

    public void advanceClock(long millis) {
      this.timestamp += millis;
    }
  }

  public static GoogleOAuthAccessToken newAccessToken(String token, long expiryTime, Clock clock) {
    return new GoogleOAuthAccessToken(token, expiryTime, clock);
  }
}
