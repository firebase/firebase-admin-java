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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.FirebaseThreadManagers;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Configurable Firebase options. */
public final class FirebaseOptions {

  private static final List<String> FIREBASE_SCOPES =
      ImmutableList.of(
          // Enables access to Firebase Realtime Database.
          "https://www.googleapis.com/auth/firebase.database",

          // Enables access to the email address associated with a project.
          "https://www.googleapis.com/auth/userinfo.email",

          // Enables access to Google Identity Toolkit (for user management APIs).
          "https://www.googleapis.com/auth/identitytoolkit",

          // Enables access to Google Cloud Storage.
          "https://www.googleapis.com/auth/devstorage.full_control",

          // Enables access to Google Cloud Firestore
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/datastore");

  private final String databaseUrl;
  private final String storageBucket;
  private final GoogleCredentials credentials;
  private final Map<String, Object> databaseAuthVariableOverride;
  private final String projectId;
  private final String serviceAccountId;
  private final HttpTransport httpTransport;
  private final int connectTimeout;
  private final int readTimeout;
  private final JsonFactory jsonFactory;
  private final ThreadManager threadManager;
  private final FirestoreOptions firestoreOptions;

  private FirebaseOptions(@NonNull FirebaseOptions.Builder builder) {
    this.credentials = checkNotNull(builder.credentials,
        "FirebaseOptions must be initialized with setCredentials().")
        .createScoped(FIREBASE_SCOPES);
    this.databaseUrl = builder.databaseUrl;
    this.databaseAuthVariableOverride = builder.databaseAuthVariableOverride;
    this.projectId = builder.projectId;
    if (!Strings.isNullOrEmpty(builder.storageBucket)) {
      checkArgument(!builder.storageBucket.startsWith("gs://"),
          "StorageBucket must not include 'gs://' prefix.");
    }
    if (!Strings.isNullOrEmpty(builder.serviceAccountId)) {
      this.serviceAccountId = builder.serviceAccountId;
    } else {
      this.serviceAccountId = null;
    }
    this.storageBucket = builder.storageBucket;
    this.httpTransport = checkNotNull(builder.httpTransport,
        "FirebaseOptions must be initialized with a non-null HttpTransport.");
    this.jsonFactory = checkNotNull(builder.jsonFactory,
        "FirebaseOptions must be initialized with a non-null JsonFactory.");
    this.threadManager = checkNotNull(builder.threadManager,
        "FirebaseOptions must be initialized with a non-null ThreadManager.");
    checkArgument(builder.connectTimeout >= 0);
    this.connectTimeout = builder.connectTimeout;
    checkArgument(builder.readTimeout >= 0);
    this.readTimeout = builder.readTimeout;
    this.firestoreOptions = builder.firestoreOptions;
  }

  /**
   * Returns the Realtime Database URL to use for data storage.
   *
   * @return The Realtime Database URL supplied via {@link Builder#setDatabaseUrl}.
   */
  public String getDatabaseUrl() {
    return databaseUrl;
  }

  /**
   * Returns the name of the Google Cloud Storage bucket used for storing application data.
   *
   * @return The cloud storage bucket name set via {@link Builder#setStorageBucket}
   */
  public String getStorageBucket() {
    return storageBucket;
  }

  GoogleCredentials getCredentials() {
    return credentials;
  }

  /**
   * Returns the <code>auth</code> variable to be used in Security Rules.
   *
   * @return The <code>auth</code> variable supplied via {@link
   *     Builder#setDatabaseAuthVariableOverride}.
   */
  public Map<String, Object> getDatabaseAuthVariableOverride() {
    return databaseAuthVariableOverride;
  }

  /**
   * Returns the Google Cloud project ID.
   *
   * @return The project ID set via {@link Builder#setProjectId(String)}
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Returns the client email address of the service account.
   *
   * @return The client email of the service account set via
   *     {@link Builder#setServiceAccountId(String)}
   */
  public String getServiceAccountId() {
    return serviceAccountId;
  }

  /**
   * Returns the <code>HttpTransport</code> used to call remote HTTP endpoints. This transport is
   * used by all services of the SDK, except for FirebaseDatabase.
   *
   * @return A Google API client <code>HttpTransport</code> instance.
   */
  @NonNull
  public HttpTransport getHttpTransport() {
    return httpTransport;
  }

  /**
   * Returns the <code>JsonFactory</code> used to parse JSON when calling remote HTTP endpoints.
   *
   * @return A Google API client <code>JsonFactory</code> instance.
   */
  @NonNull
  public JsonFactory getJsonFactory() {
    return jsonFactory;
  }

  /**
   * Returns the connect timeout in milliseconds, which is applied to outgoing REST calls
   * made by the SDK.
   *
   * @return Connect timeout in milliseconds. 0 indicates an infinite timeout.
   */
  public int getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Returns the read timeout in milliseconds, which is applied to outgoing REST calls
   * made by the SDK.
   *
   * @return Read timeout in milliseconds. 0 indicates an infinite timeout.
   */
  public int getReadTimeout() {
    return readTimeout;
  }

  @NonNull
  ThreadManager getThreadManager() {
    return threadManager;
  }

  FirestoreOptions getFirestoreOptions() {
    return firestoreOptions;
  }

  /**
   * Creates an empty builder.
   *
   * @return A new builder instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for constructing {@link FirebaseOptions}. 
   */
  public static final class Builder {
    @Key("databaseAuthVariableOverride")
    private Map<String, Object> databaseAuthVariableOverride = new HashMap<>();
    
    @Key("databaseUrl")
    private String databaseUrl;

    @Key("projectId")
    private String projectId;
    
    @Key("storageBucket")
    private String storageBucket;

    @Key("serviceAccountId")
    private String serviceAccountId;
    
    private GoogleCredentials credentials;
    private FirestoreOptions firestoreOptions;
    private HttpTransport httpTransport = Utils.getDefaultTransport();
    private JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    private ThreadManager threadManager = FirebaseThreadManagers.DEFAULT_THREAD_MANAGER;
    private int connectTimeout;
    private int readTimeout;

    /** Constructs an empty builder. */
    public Builder() {}

    /**
     * Initializes the builder's values from the options object.
     *
     * <p>The new builder is not backed by this object's values, that is changes made to the new
     * builder don't change the values of the origin object.
     */
    public Builder(FirebaseOptions options) {
      databaseUrl = options.databaseUrl;
      storageBucket = options.storageBucket;
      credentials = options.credentials;
      databaseAuthVariableOverride = options.databaseAuthVariableOverride;
      projectId = options.projectId;
      httpTransport = options.httpTransport;
      jsonFactory = options.jsonFactory;
      threadManager = options.threadManager;
      connectTimeout = options.connectTimeout;
      readTimeout = options.readTimeout;
      firestoreOptions = options.firestoreOptions;
    }

    /**
     * Sets the Realtime Database URL to use for data storage.
     *
     * <p>See <a href="https://firebase.google.com/docs/admin/setup#initialize_the_sdk">
     * Initialize the SDK</a> for code samples and detailed documentation.
     *
     * @param databaseUrl The Realtime Database URL to use for data storage.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setDatabaseUrl(@Nullable String databaseUrl) {
      this.databaseUrl = databaseUrl;
      return this;
    }

    /**
     * Sets the name of the Google Cloud Storage bucket for reading and writing application data.
     * This should be the full name of the bucket as listed in the
     * <a href="https://console.cloud.google.com">Google Cloud Platform Console</a>, and must not
     * include {@code gs://} or any other protocol prefixes.
     * The same credential used to initialize the SDK (see {@link Builder#setCredentials}) is
     * used to access the bucket.
     *
     * <p>See <a href="https://firebase.google.com/docs/storage/admin/start">
     * Introduction to the Admin Cloud Storage API</a> for code samples and detailed documentation.
     *
     * @param storageBucket The full name of an existing Google Cloud Storage bucket, excluding any
     *     protocol prefixes.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setStorageBucket(String storageBucket) {
      checkArgument(!Strings.isNullOrEmpty(storageBucket),
          "Storage bucket must not be null or empty");
      this.storageBucket = storageBucket;
      return this;
    }

    /**
     * Sets the <code>GoogleCredentials</code> to use to authenticate the SDK.
     *
     * <p>See <a href="https://firebase.google.com/docs/admin/setup#initialize_the_sdk">
     * Initialize the SDK</a> for code samples and detailed documentation.
     *
     * @param credentials A
     *     <a href="http://google.github.io/google-auth-library-java/releases/0.7.1/apidocs/com/google/auth/oauth2/GoogleCredentials.html">{@code GoogleCredentials}</a>
     *     instance used to authenticate the SDK.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setCredentials(GoogleCredentials credentials) {
      this.credentials = checkNotNull(credentials);
      return this;
    }

    /**
     * Sets the <code>auth</code> variable to be used by the Realtime Database rules.
     *
     * <p>When set, security rules for Realtime Database actions are evaluated using the provided
     * auth object. During evaluation the object is available on the <code>auth</code> variable. Use
     * this option to enforce schema validation and additional security for this app instance.
     *
     * <p>If this option is not provided, security rules are bypassed entirely for this app
     * instance. If this option is set to <code>null</code>, security rules are evaluated against an
     * unauthenticated user. That is, the <code>auth</code> variable is <code>null</code>.
     *
     * <p>See <a href="https://firebase.google.com/docs/database/admin/start#authenticate-with-limited-privileges">
     * Authenticate with limited privileges</a> for code samples and detailed documentation.
     *
     * @param databaseAuthVariableOverride The value to use for the <code>auth</code> variable in
     *     the security rules for Realtime Database actions.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setDatabaseAuthVariableOverride(
        @Nullable Map<String, Object> databaseAuthVariableOverride) {
      this.databaseAuthVariableOverride = databaseAuthVariableOverride;
      return this;
    }

    /**
     * Sets the Google Cloud project ID that should be associated with an app.
     *
     * @param projectId A non-null, non-empty project ID string.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setProjectId(@NonNull String projectId) {
      checkArgument(!Strings.isNullOrEmpty(projectId), "Project ID must not be null or empty");
      this.projectId = projectId;
      return this;
    }

    /**
     * Sets the client email address of the service account that should be associated with an app.
     *
     * <p>This is used to <a href="https://firebase.google.com/docs/auth/admin/create-custom-tokens">
     * create custom auth tokens</a> when service account credentials are not available. The client
     * email address of a service account can be found in the {@code client_email} field of the
     * service account JSON.
     *
     * @param serviceAccountId A service account email address string.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setServiceAccountId(@NonNull String serviceAccountId) {
      checkArgument(!Strings.isNullOrEmpty(serviceAccountId),
          "Service account ID must not be null or empty");
      this.serviceAccountId = serviceAccountId;
      return this;
    }

    /**
     * Sets the <code>HttpTransport</code> used to make remote HTTP calls. A reasonable default
     * is used if not explicitly set. The transport specified by calling this method is
     * used by all services of the SDK, except for <code>FirebaseDatabase</code>.
     *
     * @param httpTransport An <code>HttpTransport</code> instance
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setHttpTransport(HttpTransport httpTransport) {
      this.httpTransport = httpTransport;
      return this;
    }

    /**
     * Sets the <code>JsonFactory</code> used to parse JSON when making remote HTTP calls. A
     * reasonable default is used if not explicitly set.
     *
     * @param jsonFactory A <code>JsonFactory</code> instance.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    /**
     * Sets the <code>ThreadManager</code> used to initialize thread pools and thread factories
     * for Firebase apps.
     *
     * @param threadManager A <code>ThreadManager</code> instance.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setThreadManager(ThreadManager threadManager) {
      this.threadManager = threadManager;
      return this;
    }

    /**
     * Sets the <code>FirestoreOptions</code> used to initialize Firestore in the
     * {@link com.google.firebase.cloud.FirestoreClient} API. This can be used to customize
     * low-level transport (GRPC) parameters, and timestamp handling behavior.
     *
     * <p>If credentials or a project ID is set in <code>FirestoreOptions</code>, they will get
     * overwritten by the corresponding parameters in <code>FirebaseOptions</code>.
     *
     * @param firestoreOptions A <code>FirestoreOptions</code> instance.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setFirestoreOptions(FirestoreOptions firestoreOptions) {
      this.firestoreOptions = firestoreOptions;
      return this;
    }

    /**
     * Sets the connect timeout for outgoing HTTP (REST) connections made by the SDK. This is used
     * when opening a communication link to a remote HTTP endpoint. This setting does not
     * affect the {@link com.google.firebase.database.FirebaseDatabase} and
     * {@link com.google.firebase.cloud.FirestoreClient} APIs.
     *
     * @param connectTimeout Connect timeout in milliseconds. Must not be negative.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setConnectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Sets the read timeout for outgoing HTTP (REST) calls made by the SDK. This does not affect
     * the {@link com.google.firebase.database.FirebaseDatabase} and
     * {@link com.google.firebase.cloud.FirestoreClient} APIs.
     *
     * @param readTimeout Read timeout in milliseconds. Must not be negative.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setReadTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    /**
     * Builds the {@link FirebaseOptions} instance from the previously set options.
     *
     * @return A {@link FirebaseOptions} instance created from the previously set options.
     */
    public FirebaseOptions build() {
      return new FirebaseOptions(this);
    }
  }
}
