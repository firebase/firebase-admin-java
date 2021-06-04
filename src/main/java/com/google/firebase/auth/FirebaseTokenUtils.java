/*
 * Copyright  2019 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.internal.CryptoSigners;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.internal.Nullable;

import java.io.IOException;

final class FirebaseTokenUtils {

  private static final String ID_TOKEN_CERT_URL =
      "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
  private static final String ID_TOKEN_ISSUER_PREFIX = "https://securetoken.google.com/";

  private static final String SESSION_COOKIE_CERT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/publicKeys";
  private static final String SESSION_COOKIE_ISSUER_PREFIX = "https://session.firebase.google.com/";

  // The default JsonFactory implementation we get from Google API client does not support parsing
  // JSON strings with control characters in text. The public key certificates we get from Google
  // auth servers contain some control characters, and therefore we must use a JsonFactory that is
  // capable of parsing such text.
  static final JsonFactory UNQUOTED_CTRL_CHAR_JSON_FACTORY = new GsonFactory();

  private FirebaseTokenUtils() { }

  static FirebaseTokenFactory createTokenFactory(FirebaseApp firebaseApp, Clock clock) {
    return createTokenFactory(firebaseApp, clock, null);
  }

  static FirebaseTokenFactory createTokenFactory(
      FirebaseApp firebaseApp, Clock clock, @Nullable String tenantId) {
    try {
      return new FirebaseTokenFactory(
          firebaseApp.getOptions().getJsonFactory(),
          clock,
          CryptoSigners.getCryptoSigner(firebaseApp),
          tenantId);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to initialize FirebaseTokenFactory. Make sure to initialize the SDK "
              + "with service account credentials or specify a service account "
              + "ID with iam.serviceAccounts.signBlob permission. Please refer to "
              + "https://firebase.google.com/docs/auth/admin/create-custom-tokens for more "
              + "details on creating custom tokens.", e);
    }
  }

  static FirebaseTokenVerifierImpl createIdTokenVerifier(FirebaseApp app, Clock clock) {
    return createIdTokenVerifier(app, clock, null);
  }

  static FirebaseTokenVerifierImpl createIdTokenVerifier(
      FirebaseApp app, Clock clock, @Nullable String tenantId) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkState(!Strings.isNullOrEmpty(projectId),
        "Must initialize FirebaseApp with a project ID to call verifyIdToken()");
    IdTokenVerifier idTokenVerifier = newIdTokenVerifier(
        clock, ID_TOKEN_ISSUER_PREFIX, projectId);
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(
        app.getOptions(), clock, ID_TOKEN_CERT_URL);
    return FirebaseTokenVerifierImpl.builder()
        .setShortName("ID token")
        .setMethod("verifyIdToken()")
        .setDocUrl("https://firebase.google.com/docs/auth/admin/verify-id-tokens")
        .setJsonFactory(app.getOptions().getJsonFactory())
        .setPublicKeysManager(publicKeysManager)
        .setIdTokenVerifier(idTokenVerifier)
        .setInvalidTokenErrorCode(AuthErrorCode.INVALID_ID_TOKEN)
        .setExpiredTokenErrorCode(AuthErrorCode.EXPIRED_ID_TOKEN)
        .setTenantId(tenantId)
        .build();
  }

  static FirebaseTokenVerifierImpl createSessionCookieVerifier(FirebaseApp app, Clock clock) {
    return createSessionCookieVerifier(app, clock, null);
  }

  static FirebaseTokenVerifierImpl createSessionCookieVerifier(
      FirebaseApp app, Clock clock, @Nullable String tenantId) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkState(!Strings.isNullOrEmpty(projectId),
        "Must initialize FirebaseApp with a project ID to call verifySessionCookie()");
    IdTokenVerifier idTokenVerifier = newIdTokenVerifier(
        clock, SESSION_COOKIE_ISSUER_PREFIX, projectId);
    GooglePublicKeysManager publicKeysManager = newPublicKeysManager(
        app.getOptions(), clock, SESSION_COOKIE_CERT_URL);
    return FirebaseTokenVerifierImpl.builder()
        .setShortName("session cookie")
        .setMethod("verifySessionCookie()")
        .setDocUrl("https://firebase.google.com/docs/auth/admin/manage-cookies")
        .setInvalidTokenErrorCode(AuthErrorCode.INVALID_SESSION_COOKIE)
        .setExpiredTokenErrorCode(AuthErrorCode.EXPIRED_SESSION_COOKIE)
        .setJsonFactory(app.getOptions().getJsonFactory())
        .setPublicKeysManager(publicKeysManager)
        .setIdTokenVerifier(idTokenVerifier)
        .setTenantId(tenantId)
        .build();
  }

  private static GooglePublicKeysManager newPublicKeysManager(
      FirebaseOptions options, Clock clock, String certUrl) {
    return new GooglePublicKeysManager.Builder(
        options.getHttpTransport(), UNQUOTED_CTRL_CHAR_JSON_FACTORY)
        .setClock(clock)
        .setPublicCertsEncodedUrl(certUrl)
        .build();
  }

  private static IdTokenVerifier newIdTokenVerifier(
      Clock clock, String issuerPrefix, String projectId) {
    return new IdTokenVerifier.Builder()
        .setClock(clock)
        .setAudience(ImmutableList.of(projectId))
        .setIssuer(issuerPrefix + projectId)
        .build();
  }
}
