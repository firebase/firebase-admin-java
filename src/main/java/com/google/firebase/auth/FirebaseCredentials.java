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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.base.Strings;
import com.google.firebase.auth.internal.BaseCredential;
import com.google.firebase.internal.NonNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Standard {@link FirebaseCredential} implementations for use with {@link
 * com.google.firebase.FirebaseOptions}.
 *
 * @deprecated Use <a href="http://google.github.io/google-auth-library-java/releases/0.7.1/apidocs/com/google/auth/oauth2/GoogleCredentials.html">{@code GoogleCredentials}</a>.
 */
public class FirebaseCredentials {

  private FirebaseCredentials() {
  }

  /**
   * Returns a {@link FirebaseCredential} based on Google Application Default Credentials which can
   * be used to authenticate the SDK.
   *
   * <p>See <a
   * href="https://developers.google.com/identity/protocols/application-default-credentials">Google
   * Application Default Credentials</a> for details on Google Application Deafult Credentials.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @return A {@link FirebaseCredential} based on Google Application Default Credentials which can
   *     be used to authenticate the SDK.
   */
  @NonNull
  public static FirebaseCredential applicationDefault() {
    return DefaultCredentialsHolder.INSTANCE;
  }

  /**
   * Returns a {@link FirebaseCredential} based on Google Application Default Credentials which can
   * be used to authenticate the SDK. Allows specifying the <code>HttpTransport</code> and the
   * <code>JsonFactory</code> to be used when communicating with the remote authentication server.
   *
   * <p>See <a
   * href="https://developers.google.com/identity/protocols/application-default-credentials">Google
   * Application Default Credentials</a> for details on Google Application Deafult Credentials.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param transport <code>HttpTransport</code> used to communicate with the remote
   *     authentication server.
   * @param jsonFactory <code>JsonFactory</code> used to parse JSON responses from the remote
   *     authentication server.
   * @return A {@link FirebaseCredential} based on Google Application Default Credentials which can
   *     be used to authenticate the SDK.
   */
  @NonNull
  public static FirebaseCredential applicationDefault(
      HttpTransport transport, JsonFactory jsonFactory) {
    try {
      return new ApplicationDefaultCredential(transport);
    } catch (IOException e) {
      // To prevent a breaking API change, we throw an unchecked exception.
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a {@link FirebaseCredential} generated from the provided service account certificate
   * which can be used to authenticate the SDK.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param serviceAccount An <code>InputStream</code> containing the JSON representation of a
   *     service account certificate.
   * @return A {@link FirebaseCredential} generated from the provided service account certificate
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the service account certificate.
   */
  @NonNull
  public static FirebaseCredential fromCertificate(InputStream serviceAccount) throws IOException {
    return fromCertificate(serviceAccount, Utils.getDefaultTransport(),
        Utils.getDefaultJsonFactory());
  }

  /**
   * Returns a {@link FirebaseCredential} generated from the provided service account certificate
   * which can be used to authenticate the SDK. Allows specifying the <code>HttpTransport</code>
   * and the <code>JsonFactory</code> to be used when communicating with the remote authentication
   * server.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param serviceAccount An <code>InputStream</code> containing the JSON representation of a
   *     service account certificate.
   * @param transport <code>HttpTransport</code> used to communicate with the remote
   *     authentication server.
   * @param jsonFactory <code>JsonFactory</code> used to parse JSON responses from the remote
   *     authentication server.
   * @return A {@link FirebaseCredential} generated from the provided service account certificate
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the service account certificate.
   */
  @NonNull
  public static FirebaseCredential fromCertificate(InputStream serviceAccount,
      HttpTransport transport, JsonFactory jsonFactory) throws IOException {
    ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
        serviceAccount, wrap(transport));
    checkArgument(!Strings.isNullOrEmpty(credentials.getProjectId()),
        "Failed to parse service account: 'project_id' must be set");
    return new CertCredential(credentials);
  }

  /**
   * Returns a {@link FirebaseCredential} generated from the provided refresh token which can be
   * used to authenticate the SDK.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param refreshToken An <code>InputStream</code> containing the JSON representation of a refresh
   *     token.
   * @return A {@link FirebaseCredential} generated from the provided service account credential
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the refresh token.
   */
  @NonNull
  public static FirebaseCredential fromRefreshToken(InputStream refreshToken) throws IOException {
    return fromRefreshToken(
        refreshToken, Utils.getDefaultTransport(), Utils.getDefaultJsonFactory());
  }

  /**
   * Returns a {@link FirebaseCredential} generated from the provided refresh token which can be
   * used to authenticate the SDK. Allows specifying the <code>HttpTransport</code> and the
   * <code>JsonFactory</code> to be used when communicating with the remote authentication server.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param refreshToken An <code>InputStream</code> containing the JSON representation of a refresh
   *     token.
   * @param transport <code>HttpTransport</code> used to communicate with the remote
   *     authentication server.
   * @param jsonFactory <code>JsonFactory</code> used to parse JSON responses from the remote
   *     authentication server.
   * @return A {@link FirebaseCredential} generated from the provided service account credential
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the refresh token.
   */
  @NonNull
  public static FirebaseCredential fromRefreshToken(final InputStream refreshToken,
      HttpTransport transport, JsonFactory jsonFactory) throws IOException {
    return new RefreshTokenCredential(refreshToken, transport);
  }

  static class CertCredential extends BaseCredential {

    CertCredential(ServiceAccountCredentials credentials) throws IOException {
      super(credentials);
    }
  }

  static class ApplicationDefaultCredential extends BaseCredential {

    ApplicationDefaultCredential(HttpTransport transport) throws IOException {
      super(GoogleCredentials.getApplicationDefault(wrap(transport)));
    }
  }

  static class RefreshTokenCredential extends BaseCredential {

    RefreshTokenCredential(InputStream inputStream, HttpTransport transport) throws IOException {
      super(UserCredentials.fromStream(inputStream, wrap(transport)));
    }
  }

  private static class DefaultCredentialsHolder {

    static final FirebaseCredential INSTANCE =
        applicationDefault(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory());
  }

  private static HttpTransportFactory wrap(final HttpTransport transport) {
    checkNotNull(transport, "HttpTransport must not be null");
    return new HttpTransportFactory() {
      @Override
      public HttpTransport create() {
        return transport;
      }
    };
  }
}
