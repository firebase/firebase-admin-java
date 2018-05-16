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
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.auth.internal.CryptoSigner.IAMCryptoSigner;
import com.google.firebase.auth.internal.CryptoSigner.ServiceAccountCryptoSigner;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestResponseInterceptor;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;

public class CryptoSignerTest {

  @Test
  public void testServiceAccountCryptoSigner() throws IOException {
    ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
        ServiceAccount.EDITOR.asStream());
    byte[] expected = credentials.sign("foo".getBytes());
    CryptoSigner signer = new ServiceAccountCryptoSigner(credentials);
    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals(expected, data);
  }

  @Test
  public void testIAMCryptoSigner() throws IOException {
    String signature = BaseEncoding.base64().encode("signed-bytes".getBytes());
    String response = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signature", signature));
    MockHttpTransport transport = new MultiRequestMockHttpTransport(
        ImmutableList.of(new MockLowLevelHttpResponse().setContent(response)));
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setServiceAccount("test-service-account@iam.gserviceaccount.com")
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    CryptoSigner signer = new IAMCryptoSigner(app, interceptor);
    assertNull(interceptor.getResponse());

    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("signed-bytes".getBytes(), data);
    final String url = "https://iam.googleapis.com/v1/projects/-/serviceAccounts/"
        + "test-service-account@iam.gserviceaccount.com:signBlob";
    assertEquals(url, interceptor.getResponse().getRequest().getUrl().toString());
  }

  @Test
  public void testIAMCryptoSignerNoServiceAccount() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    try {
      new IAMCryptoSigner(app);
      fail("No error thrown when service account not specified");
    } catch (IOException expected) {
      // expected
    }
  }

  @Test
  public void testIAMCryptoSignerMetadataService() throws IOException {
    String signature = BaseEncoding.base64().encode("signed-bytes".getBytes());
    String response = Utils.getDefaultJsonFactory().toString(
        ImmutableMap.of("signature", signature));
    MockHttpTransport transport = new MultiRequestMockHttpTransport(
        ImmutableList.of(
            new MockLowLevelHttpResponse().setContent("metadata-server@iam.gserviceaccount.com"),
            new MockLowLevelHttpResponse().setContent(response)));
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    CryptoSigner signer = new IAMCryptoSigner(app, interceptor);
    assertNotNull(interceptor.getResponse());
    assertEquals("Google", interceptor.getResponse().getRequest()
        .getHeaders().get("Metadata-Flavor"));

    byte[] data = signer.sign("foo".getBytes());
    assertArrayEquals("signed-bytes".getBytes(), data);
    final String url = "https://iam.googleapis.com/v1/projects/-/serviceAccounts/"
        + "metadata-server@iam.gserviceaccount.com:signBlob";
    assertEquals(url, interceptor.getResponse().getRequest().getUrl().toString());
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }
}
