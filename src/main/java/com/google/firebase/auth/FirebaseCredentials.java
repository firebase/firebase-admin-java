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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Standard {@link FirebaseCredential} implementations for use with {@link
 * com.google.firebase.FirebaseOptions}.
 */
public class FirebaseCredentials {

  private static final List<String> FIREBASE_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/firebase.database",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/identitytoolkit");

  private FirebaseCredentials() {
  }

  private static String streamToString(InputStream inputStream) throws IOException {
    InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    return CharStreams.toString(reader);
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
   * be used to authenticate the SDK. Allows specifying the HttpTransport and the
   * JsonFactory to be used when communicating with the remote authentication server.
   *
   * <p>See <a
   * href="https://developers.google.com/identity/protocols/application-default-credentials">Google
   * Application Default Credentials</a> for details on Google Application Deafult Credentials.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param transport HttpTransport used to communicate with the remote authentication server.
   * @param jsonFactory JsonFactory used to parse JSON responses from the remote authentication
   * @return A {@link FirebaseCredential} based on Google Application Default Credentials which can
   *     be used to authenticate the SDK.
   */
  @NonNull
  public static FirebaseCredential applicationDefault(
      HttpTransport transport, JsonFactory jsonFactory) {
    return new ApplicationDefaultCredential(transport, jsonFactory);
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
   * which can be used to authenticate the SDK. Allows specifying the HttpTransport and the
   * JsonFactory to be used when communicating with the remote authentication server.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param serviceAccount An <code>InputStream</code> containing the JSON representation of a
   *     service account certificate.
   * @param transport HttpTransport used to communicate with the remote authentication server.
   * @param jsonFactory JsonFactory used to parse JSON responses from the remote authentication
   *     server.
   * @return A {@link FirebaseCredential} generated from the provided service account certificate
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the service account certificate.
   */
  @NonNull
  public static FirebaseCredential fromCertificate(InputStream serviceAccount,
      HttpTransport transport, JsonFactory jsonFactory) throws IOException {
    return new CertCredential(serviceAccount, transport, jsonFactory);
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
   * used to authenticate the SDK. Allows specifying the HttpTransport and the
   * JsonFactory to be used when communicating with the remote authentication server.
   *
   * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
   * and detailed documentation.
   *
   * @param refreshToken An <code>InputStream</code> containing the JSON representation of a refresh
   *     token.
   * @param transport HttpTransport used to communicate with the remote authentication server.
   * @param jsonFactory JsonFactory used to parse JSON responses from the remote authentication
   *     server.
   * @return A {@link FirebaseCredential} generated from the provided service account credential
   *     which can be used to authenticate the SDK.
   * @throws IOException If an error occurs while parsing the refresh token.
   */
  @NonNull
  public static FirebaseCredential fromRefreshToken(final InputStream refreshToken,
      HttpTransport transport, JsonFactory jsonFactory) throws IOException {
    return new RefreshTokenCredential(refreshToken, transport, jsonFactory);
  }

  /**
   * Helper class that implements {@link FirebaseCredential} on top of {@link GoogleCredential} and
   * provides caching of access tokens and credentials.
   */
  abstract static class BaseCredential implements FirebaseCredential {

    final HttpTransport transport;
    final JsonFactory jsonFactory;
    private GoogleCredential googleCredential;

    BaseCredential(HttpTransport transport, JsonFactory jsonFactory) {
      this.transport = checkNotNull(transport, "HttpTransport must not be null");
      this.jsonFactory = checkNotNull(jsonFactory, "JsonFactory must not be null");
    }

    /** Retrieves a GoogleCredential. Should not use caching. */
    abstract GoogleCredential fetchCredential() throws IOException;

    /**
     * Returns the associated GoogleCredential for this class. This implementation is cached by
     * default.
     */
    final Task<GoogleCredential> getCertificate() {
      synchronized (this) {
        if (googleCredential != null) {
          return Tasks.forResult(googleCredential);
        }
      }

      return Tasks.call(
          new Callable<GoogleCredential>() {
            @Override
            public GoogleCredential call() throws Exception {
              // Retrieve a new credential. This is a network operation that can be repeated and is
              // done outside of the lock.
              GoogleCredential credential = fetchCredential();
              synchronized (BaseCredential.this) {
                googleCredential = credential;
              }
              return credential;
            }
          });
    }

    abstract GoogleOAuthAccessToken fetchToken(GoogleCredential credential) throws IOException;

    /**
     * Returns an access token for this credential. Does not cache tokens.
     */
    @Override
    public final Task<GoogleOAuthAccessToken> getAccessToken() {
      return getCertificate()
          .continueWith(new Continuation<GoogleCredential, GoogleOAuthAccessToken>() {
            @Override
            public GoogleOAuthAccessToken then(@NonNull Task<GoogleCredential> task)
                throws Exception {
              return fetchToken(task.getResult());
            }
          });
    }
  }

  static class CertCredential extends BaseCredential {

    private final String jsonData;
    private final String projectId;

    CertCredential(InputStream inputStream, HttpTransport transport,
        JsonFactory jsonFactory) throws IOException {
      super(transport, jsonFactory);
      jsonData = streamToString(checkNotNull(inputStream));
      JSONObject jsonObject = new JSONObject(jsonData);
      try {
        projectId = jsonObject.getString("project_id");
      } catch (JSONException e) {
        throw new IOException("Failed to parse service account: 'project_id' must be set", e);
      }
    }

    @Override
    GoogleCredential fetchCredential() throws IOException {
      GoogleCredential firebaseCredential =
          GoogleCredential.fromStream(
              new ByteArrayInputStream(jsonData.getBytes("UTF-8")), transport, jsonFactory);

      if (firebaseCredential.getServiceAccountId() == null) {
        throw new IOException(
            "Error reading credentials from stream, 'type' value 'service_account' not "
                + "recognized. Expecting 'authorized_user'.");
      }

      return firebaseCredential.createScoped(FIREBASE_SCOPES);
    }

    @Override
    GoogleOAuthAccessToken fetchToken(GoogleCredential credential) throws IOException {
      credential.refreshToken();
      return newAccessToken(credential);
    }

    Task<String> getProjectId() {
      return Tasks.forResult(projectId);
    }
  }

  static class ApplicationDefaultCredential extends BaseCredential {

    ApplicationDefaultCredential(HttpTransport transport, JsonFactory jsonFactory) {
      super(transport, jsonFactory);
    }

    @Override
    GoogleCredential fetchCredential() throws IOException {
      return GoogleCredential.getApplicationDefault(transport, jsonFactory)
          .createScoped(FIREBASE_SCOPES);
    }

    @Override
    GoogleOAuthAccessToken fetchToken(GoogleCredential credential) throws IOException {
      credential.refreshToken();
      return newAccessToken(credential);
    }
  }

  static class RefreshTokenCredential extends BaseCredential {

    private final String jsonData;

    RefreshTokenCredential(InputStream inputStream, HttpTransport transport,
        JsonFactory jsonFactory) throws IOException {
      super(transport, jsonFactory);
      jsonData = streamToString(checkNotNull(inputStream));
    }

    @Override
    GoogleCredential fetchCredential() throws IOException {
      GoogleCredential credential =
          GoogleCredential.fromStream(
              new ByteArrayInputStream(jsonData.getBytes("UTF-8")), transport, jsonFactory);

      if (credential.getServiceAccountId() != null) {
        throw new IOException(
            "Error reading credentials from stream, 'type' value 'authorized_user' not "
                + "recognized. Expecting 'service_account'.");
      }

      return credential;
    }

    @Override
    GoogleOAuthAccessToken fetchToken(GoogleCredential credential) throws IOException {
      credential.refreshToken();
      return newAccessToken(credential);
    }
  }

  private static class DefaultCredentialsHolder {

    static final FirebaseCredential INSTANCE =
        applicationDefault(Utils.getDefaultTransport(), Utils.getDefaultJsonFactory());
  }

  static GoogleOAuthAccessToken newAccessToken(GoogleCredential credential) {
    checkNotNull(credential);
    return new GoogleOAuthAccessToken(credential.getAccessToken(),
        credential.getExpirationTimeMilliseconds());
  }
}
