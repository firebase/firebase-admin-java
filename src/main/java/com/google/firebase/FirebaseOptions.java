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
import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Configurable Firebase options. */
public final class FirebaseOptions {

  // TODO: deprecate and remove it once we can fetch these from Remote Config.

  private final String databaseUrl;
  private final String storageBucket;
  private final FirebaseCredential firebaseCredential;
  private final Map<String, Object> databaseAuthVariableOverride;
  private final HttpTransport httpTransport;
  private final JsonFactory jsonFactory;

  private FirebaseOptions(@NonNull FirebaseOptions.Builder builder) {
    this.firebaseCredential = checkNotNull(builder.firebaseCredential,
        "FirebaseOptions must be initialized with setCredential().");
    this.databaseUrl = builder.databaseUrl;
    this.databaseAuthVariableOverride = builder.databaseAuthVariableOverride;
    this.storageBucket = builder.storageBucket;
    this.httpTransport = checkNotNull(builder.httpTransport,
        "FirebaseOptions must be initialized with a non-null HttpTransport.");
    this.jsonFactory = checkNotNull(builder.jsonFactory,
        "FirebaseOptions must be initialized with a non-null JsonFactory.");
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

  FirebaseCredential getCredential() {
    return firebaseCredential;
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
   * Builder for constructing {@link FirebaseOptions}. 
   */
  public static final class Builder {

    private String databaseUrl;
    private String storageBucket;
    private FirebaseCredential firebaseCredential;
    private Map<String, Object> databaseAuthVariableOverride = new HashMap<>();
    private HttpTransport httpTransport = Utils.getDefaultTransport();
    private JsonFactory jsonFactory = Utils.getDefaultJsonFactory();

    /** Constructs an empty builder. */
    public Builder() {}

    /**
     * Initializes the builder's values from the options object. *
     *
     * <p>The new builder is not backed by this objects values, that is changes made to the new
     * builder don't change the values of the origin object.
     */
    public Builder(FirebaseOptions options) {
      databaseUrl = options.databaseUrl;
      storageBucket = options.storageBucket;
      firebaseCredential = options.firebaseCredential;
      databaseAuthVariableOverride = options.databaseAuthVariableOverride;
      httpTransport = options.httpTransport;
      jsonFactory = options.jsonFactory;
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
     * The same credential used to initialize the SDK (see {@link Builder#setCredential}) will be
     * used to access the bucket.
     *
     * <p>See <a href="https://firebase.google.com/docs/admin/setup#initialize_the_sdk">
     * Initialize the SDK</a> for code samples and detailed documentation.
     *
     * @param storageBucket The name of an existing Google Cloud Storage bucket.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setStorageBucket(String storageBucket) {
      checkArgument(!Strings.isNullOrEmpty(storageBucket),
          "Storage bucket must not be null or empty");
      this.storageBucket = storageBucket;
      return this;
    }

    /**
     * Sets the <code>FirebaseCredential</code> to use to authenticate the SDK.
     *
     * <p>See <a href="https://firebase.google.com/docs/admin/setup#initialize_the_sdk">
     * Initialize the SDK</a> for code samples and detailed documentation.
     *
     * @param credential A <code>FirebaseCredential</code> used to authenticate the SDK. See {@link
     *     FirebaseCredentials} for default implementations.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setCredential(@NonNull FirebaseCredential credential) {
      firebaseCredential = checkNotNull(credential);
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
     * Builds the {@link FirebaseOptions} instance from the previously set options.
     *
     * @return A {@link FirebaseOptions} instance created from the previously set options.
     */
    public FirebaseOptions build() {
      return new FirebaseOptions(this);
    }
  }
}
