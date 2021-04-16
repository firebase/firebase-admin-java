/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.testing.ServiceAccount;

class FirebaseTokenVerifierImplTestUtils {
  public static final String TEST_TOKEN_ISSUER = "https://test.token.issuer";

  static void checkInvalidTokenException(FirebaseAuthException e, String message) {
    checkException(e, message, AuthErrorCode.INVALID_ID_TOKEN);
  }

  static void checkException(FirebaseAuthException e, String message, AuthErrorCode errorCode) {
    assertEquals(ErrorCode.INVALID_ARGUMENT, e.getErrorCode());
    assertEquals(message, e.getMessage());
    assertNull(e.getCause());
    assertNull(e.getHttpResponse());
    assertEquals(errorCode, e.getAuthErrorCode());
  }

  static GooglePublicKeysManager newPublicKeysManager(String certificate) {
    String serviceAccountCertificates =
        String.format("{\"%s\" : \"%s\"}", TestTokenFactory.PRIVATE_KEY_ID, certificate);
    HttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContent(serviceAccountCertificates))
        .build();
    return newPublicKeysManager(transport);
  }

  static GooglePublicKeysManager newPublicKeysManager(HttpTransport transport) {
    return new GooglePublicKeysManager.Builder(
        transport, FirebaseTokenUtils.UNQUOTED_CTRL_CHAR_JSON_FACTORY)
        .setClock(TestTokenFactory.CLOCK)
        .setPublicCertsEncodedUrl("https://test.cert.url")
        .build();
  }

  static FirebaseTokenVerifier newTestTokenVerifier(GooglePublicKeysManager publicKeysManager) {
    return fullyPopulatedBuilder()
        .setPublicKeysManager(publicKeysManager)
        .build();
  }

  static FirebaseTokenVerifierImpl.Builder fullyPopulatedBuilder() {
    return FirebaseTokenVerifierImpl.builder()
        .setShortName("test token")
        .setMethod("verifyTestToken()")
        .setDocUrl("https://test.doc.url")
        .setJsonFactory(TestTokenFactory.JSON_FACTORY)
        .setPublicKeysManager(newPublicKeysManager(ServiceAccount.EDITOR.getCert()))
        .setInvalidTokenErrorCode(AuthErrorCode.INVALID_ID_TOKEN)
        .setExpiredTokenErrorCode(AuthErrorCode.EXPIRED_ID_TOKEN)
        .setIdTokenVerifier(newIdTokenVerifier());
  }

  static IdTokenVerifier newIdTokenVerifier() {
    return new IdTokenVerifier.Builder()
        .setClock(TestTokenFactory.CLOCK)
        .setAudience(ImmutableList.of(TestTokenFactory.PROJECT_ID))
        .setIssuer(TEST_TOKEN_ISSUER)
        .build();
  }
}
