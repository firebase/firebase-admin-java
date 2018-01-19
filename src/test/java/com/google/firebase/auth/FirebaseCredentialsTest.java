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

import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.internal.BaseCredential;
import com.google.firebase.auth.internal.FirebaseCredentialsAdapter;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.BeforeClass;
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
  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void setupClass() throws IOException {
    TestUtils.getApplicationDefaultCredentials();
  }

  @Test(expected = NullPointerException.class)
  public void testNullCertificate() throws IOException {
    FirebaseCredentials.fromCertificate(null);
  }

  @Test(expected = NullPointerException.class)
  public void testNullRefreshToken() throws IOException {
    FirebaseCredentials.fromRefreshToken(null);
  }

  @Test
  public void defaultCredentialDoesntRefetch() throws Exception {
    FirebaseCredential credential = FirebaseCredentials.applicationDefault(
        Utils.getDefaultTransport(), Utils.getDefaultJsonFactory());
    GoogleOAuthAccessToken token = Tasks.await(credential.getAccessToken());
    Assert.assertEquals(TestUtils.TEST_ADC_ACCESS_TOKEN, token.getAccessToken());
    Assert.assertNotNull(((BaseCredential) credential).getGoogleCredentials());

    // We should still be able to fetch the token since the certificate is cached
    credential = FirebaseCredentials.applicationDefault();
    token = Tasks.await(credential.getAccessToken());
    Assert.assertNotNull(token);
    Assert.assertEquals(TestUtils.TEST_ADC_ACCESS_TOKEN, token.getAccessToken());
    Assert.assertNotNull(((BaseCredential) credential).getGoogleCredentials());
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

    Assert.assertNotNull(((BaseCredential) credential).getGoogleCredentials());
    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken()).getAccessToken());
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

    try {
      FirebaseCredentials.fromCertificate(inputStream, transport, Utils.getDefaultJsonFactory());
      Assert.fail();
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(
          "Failed to parse service account: 'project_id' must be set", e.getMessage());
    }
  }

  @Test
  public void certificateReadThrowsIOException()
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
      throws ExecutionException, InterruptedException, IOException {
    MockTokenServerTransport transport = new MockTokenServerTransport();
    transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);

    Map<String, Object> secretJson = new HashMap<>();
    secretJson.put("client_id", CLIENT_ID);
    secretJson.put("client_secret", CLIENT_SECRET);
    secretJson.put("refresh_token", REFRESH_TOKEN);
    secretJson.put("type", "authorized_user");
    InputStream inputStream = new ByteArrayInputStream(JSON_FACTORY.toByteArray(secretJson));

    FirebaseCredential credential =
        FirebaseCredentials.fromRefreshToken(inputStream, transport, Utils.getDefaultJsonFactory());

    Assert.assertEquals(0, inputStream.available());
    inputStream.close();

    Assert.assertNotNull(((BaseCredential) credential).getGoogleCredentials());
    Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken()).getAccessToken());
  }

  @Test
  public void refreshTokenReadThrowsIOException()
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

  @Test(expected = Exception.class)
  public void serviceAccountUsedAsRefreshToken() throws Exception {
    FirebaseCredentials.fromRefreshToken(ServiceAccount.EDITOR.asStream());
  }

  @Test
  public void tokenNotCached() throws Exception {
    TestCredential credential = new TestCredential(new MockGoogleCredentials(ACCESS_TOKEN, 10L));

    for (long i = 0; i < 10; i++) {
      Assert.assertEquals(ACCESS_TOKEN, Tasks.await(credential.getAccessToken()).getAccessToken());
      Assert.assertEquals(i + 1, credential.getFetchCount());
    }
  }

  @Test
  public void testTokenExpiration() throws Exception {
    final MockGoogleCredentials googleCredentials = new MockGoogleCredentials(ACCESS_TOKEN);
    TestCredential credential = new TestCredential(googleCredentials);

    for (long i = 0; i < 10; i++) {
      long expiryTime = (i + 1) * 10L;
      googleCredentials.setExpiryTime(expiryTime);
      GoogleOAuthAccessToken googleToken = Tasks.await(credential.getAccessToken());
      Assert.assertEquals(ACCESS_TOKEN, googleToken.getAccessToken());
      Assert.assertEquals(expiryTime, googleToken.getExpiryTime());
    }
  }

  @Test
  public void testCustomFirebaseCredential() throws IOException {
    final Date date = new Date();
    FirebaseCredential credential = new FirebaseCredential() {
      @Override
      public Task<GoogleOAuthAccessToken> getAccessToken() {
        return Tasks.forResult(new GoogleOAuthAccessToken("token", date.getTime()));
      }
    };
    GoogleCredentials googleCredentials = new FirebaseCredentialsAdapter(credential);
    AccessToken accessToken = googleCredentials.refreshAccessToken();
    Assert.assertEquals("token", accessToken.getTokenValue());
    Assert.assertEquals(date, accessToken.getExpirationTime());
  }

  private static class TestCredential extends BaseCredential {

    private final AtomicInteger fetchCount = new AtomicInteger(0);

    TestCredential(GoogleCredentials googleCredentials) {
      super(googleCredentials);
    }

    @Override
    public Task<GoogleOAuthAccessToken> getAccessToken() {
      fetchCount.incrementAndGet();
      return super.getAccessToken();
    }

    int getFetchCount() {
      return fetchCount.get();
    }
  }
}
