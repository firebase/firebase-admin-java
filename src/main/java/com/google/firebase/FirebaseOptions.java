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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
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
  private final FirebaseCredential firebaseCredential;
  private final Map<String, Object> databaseAuthVariableOverride;

  private FirebaseOptions(
      @Nullable String databaseUrl,
      @NonNull FirebaseCredential firebaseCredential,
      @Nullable Map<String, Object> databaseAuthVariableOverride) {

    this.databaseUrl = databaseUrl;
    this.firebaseCredential = checkNotNull(firebaseCredential, "Service Account must be provided.");
    this.databaseAuthVariableOverride = databaseAuthVariableOverride;
  }

  /**
   * Returns the Realtime Database URL to use for data storage.
   *
   * @return The Realtime Database URL supplied via {@link Builder#setDatabaseUrl}.
   */
  public String getDatabaseUrl() {
    return databaseUrl;
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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FirebaseOptions)) {
      return false;
    }
    FirebaseOptions other = (FirebaseOptions) obj;
    return Objects.equal(databaseUrl, other.databaseUrl)
        && Objects.equal(firebaseCredential, other.firebaseCredential)
        && Objects.equal(databaseAuthVariableOverride, other.databaseAuthVariableOverride);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(databaseUrl, firebaseCredential, databaseAuthVariableOverride);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("databaseUrl", databaseUrl)
        .add("credential", firebaseCredential)
        .add("databaseAuthVariableOverride", databaseAuthVariableOverride)
        .toString();
  }

  /** 
   * Builder for constructing {@link FirebaseOptions}. 
   */
  public static final class Builder {

    private String databaseUrl;
    private FirebaseCredential firebaseCredential;
    private Map<String, Object> databaseAuthVariableOverride = new HashMap<>();

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
      firebaseCredential = options.firebaseCredential;
      databaseAuthVariableOverride = options.databaseAuthVariableOverride;
    }

    /**
     * Sets the Realtime Database URL to use for data storage.
     *
     * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
     * and detailed documentation.
     *
     * @param databaseUrl The Realtime Database URL to use for data storage.
     * @return This <code>Builder</code> instance is returned so subsequent calls can be chained.
     */
    public Builder setDatabaseUrl(@Nullable String databaseUrl) {
      this.databaseUrl = databaseUrl;
      return this;
    }

    /**
     * Sets the <code>FirebaseCredential</code> to use to authenticate the SDK.
     *
     * <p>See <a href="/docs/admin/setup#initialize_the_sdk">Initialize the SDK</a> for code samples
     * and detailed documentation.
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
     * <p>See <a href="/docs/database/admin/start#authenticate-with-limited-privileges">Authenticate
     * with limited privileges</a> for code samples and detailed documentation.
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
     * Builds the {@link FirebaseOptions} instance from the previously set options.
     *
     * @return A {@link FirebaseOptions} instance created from the previously set options.
     */
    public FirebaseOptions build() {
      checkArgument(firebaseCredential != null,
          "FirebaseOptions must be initialized with setCredential().");
      return new FirebaseOptions(databaseUrl, firebaseCredential, databaseAuthVariableOverride);
    }
  }
}
