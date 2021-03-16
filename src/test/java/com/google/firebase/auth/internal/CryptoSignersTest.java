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

package com.google.firebase.auth.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestResponseInterceptor;
import org.junit.After;
import org.junit.Test;

public class CryptoSignersTest {

  @Test
  public void testServiceAccountCryptoSigner() throws Exception {
    ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
        ServiceAccount.EDITOR.asStream());
    byte[] expected = credentials.sign("foo".getBytes());
    CryptoSigner signer = new CryptoSigners.ServiceAccountCryptoSigner(credentials);
    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals(expected, data);
  }

  @Test
  public void testInvalidServiceAccountCryptoSigner() {
    try {
      new CryptoSigners.ServiceAccountCryptoSigner(null);
      fail("No error thrown for null service account signer");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testIAMCryptoSigner() throws Exception {
    String signature = BaseEncoding.base64().encode("signed-bytes".getBytes());
    String response = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signedBlob", signature));
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse().setContent(response))
        .build();
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    CryptoSigners.IAMCryptoSigner signer = new CryptoSigners.IAMCryptoSigner(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        "test-service-account@iam.gserviceaccount.com");
    signer.setInterceptor(interceptor);

    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("signed-bytes".getBytes(), data);
    final String url = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/"
        + "test-service-account@iam.gserviceaccount.com:signBlob";
    assertEquals(url, interceptor.getResponse().getRequest().getUrl().toString());
  }

  @Test
  public void testIAMCryptoSignerHttpError() {
    String error = "{\"error\": {\"status\":\"INTERNAL\", \"message\": \"Test error\"}}";
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(new MockLowLevelHttpResponse()
            .setStatusCode(HttpStatusCodes.STATUS_CODE_SERVER_ERROR)
            .setContent(error))
        .build();
    CryptoSigners.IAMCryptoSigner signer = new CryptoSigners.IAMCryptoSigner(
        transport.createRequestFactory(),
        Utils.getDefaultJsonFactory(),
        "test-service-account@iam.gserviceaccount.com");
    try {
      signer.sign("foo".getBytes());
    } catch (FirebaseAuthException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals("Test error", e.getMessage());
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
      assertNull(e.getAuthErrorCode());
    }
  }

  @Test
  public void testInvalidIAMCryptoSigner() {
    try {
      new CryptoSigners.IAMCryptoSigner(null, Utils.getDefaultJsonFactory(), "test");
      fail("No error thrown for null request factory");
    } catch (NullPointerException expected) {
      // expected
    }

    MockHttpTransport transport = new MockHttpTransport();
    try {
      new CryptoSigners.IAMCryptoSigner(transport.createRequestFactory(), null, "test");
      fail("No error thrown for null json factory");
    } catch (NullPointerException expected) {
      // expected
    }

    try {
      new CryptoSigners.IAMCryptoSigner(transport.createRequestFactory(),
          Utils.getDefaultJsonFactory(), null);
      fail("No error thrown for null service account");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new CryptoSigners.IAMCryptoSigner(transport.createRequestFactory(),
          Utils.getDefaultJsonFactory(), "");
      fail("No error thrown for empty service account");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testMetadataService() throws Exception {
    String signature = BaseEncoding.base64().encode("signed-bytes".getBytes());
    String response = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signedBlob", signature));
    MockHttpTransport transport = new MultiRequestMockHttpTransport(
        ImmutableList.of(
            new MockLowLevelHttpResponse().setContent("metadata-server@iam.gserviceaccount.com"),
            new MockLowLevelHttpResponse().setContent(response)));
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    CryptoSigner signer = CryptoSigners.getCryptoSigner(app);

    assertTrue(signer instanceof CryptoSigners.IAMCryptoSigner);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    ((CryptoSigners.IAMCryptoSigner) signer).setInterceptor(interceptor);

    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("signed-bytes".getBytes(), data);
    final String url = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/"
        + "metadata-server@iam.gserviceaccount.com:signBlob";
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals(url, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
  }

  @Test
  public void testExplicitServiceAccountEmail() throws Exception {
    String signature = BaseEncoding.base64().encode("signed-bytes".getBytes());
    String response = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signedBlob", signature));

    // Explicit service account should get precedence
    MockHttpTransport transport = new MultiRequestMockHttpTransport(
        ImmutableList.of(
            new MockLowLevelHttpResponse().setContent(response)));
    FirebaseOptions options = FirebaseOptions.builder()
        .setServiceAccountId("explicit-service-account@iam.gserviceaccount.com")
        .setCredentials(new MockGoogleCredentialsWithSigner("test-token"))
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    CryptoSigner signer = CryptoSigners.getCryptoSigner(app);
    assertTrue(signer instanceof CryptoSigners.IAMCryptoSigner);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    ((CryptoSigners.IAMCryptoSigner) signer).setInterceptor(interceptor);

    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("signed-bytes".getBytes(), data);
    final String url = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/"
        + "explicit-service-account@iam.gserviceaccount.com:signBlob";
    HttpRequest request = interceptor.getResponse().getRequest();
    assertEquals(url, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
  }

  @Test
  public void testCredentialsWithSigner() throws Exception {
    // Should fall back to signing-enabled credential
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentialsWithSigner("test-token"))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "customApp");
    CryptoSigner signer = CryptoSigners.getCryptoSigner(app);
    assertTrue(signer instanceof CryptoSigners.ServiceAccountCryptoSigner);
    assertEquals("credential-signer@iam.gserviceaccount.com", signer.getAccount());
    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("local-signed-bytes".getBytes(), data);
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private static class MockGoogleCredentialsWithSigner extends MockGoogleCredentials implements
      ServiceAccountSigner {

    MockGoogleCredentialsWithSigner(String tokenValue) {
      super(tokenValue);
    }

    @Override
    public String getAccount() {
      return "credential-signer@iam.gserviceaccount.com";
    }

    @Override
    public byte[] sign(byte[] toSign) {
      return "local-signed-bytes".getBytes();
    }
  }
}
