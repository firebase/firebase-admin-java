/*
 * Copyright 2020 Google LLC
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains metadata associated with an OIDC Auth provider.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class OidcProviderConfig extends AuthProviderConfig {

  @Key("clientId")
  private String clientId;

  @Key("issuer")
  private String issuer;

  public String getClientId() {
    return clientId;
  }

  public String getIssuer() {
    return issuer;
  }

  /**
   * A specification class for creating a new OIDC Auth provider.
   *
   * <p>Set the initial attributes of the new provider by calling various setter methods available
   * in this class.
   */
  public static final class CreateRequest
      extends AuthProviderConfig.AbstractCreateRequest<CreateRequest> {

    /**
     * Creates a new {@link CreateRequest}, which can be used to create a new OIDC Auth provider.
     *
     * <p>The returned object should be passed to
     * {@link AbstractFirebaseAuth#createProviderConfig(CreateRequest)} to register the provider
     * information persistently.
     */
    public CreateRequest() { }

    /**
     * Sets the client ID for the new provider.
     *
     * @param clientId a non-null, non-empty client ID string.
     */
    public CreateRequest setClientId(String clientId) {
      checkArgument(!Strings.isNullOrEmpty(clientId), "client ID must not be null or empty");
      properties.put("clientId", clientId);
      return this;
    }

    /**
     * Sets the issuer for the new provider.
     *
     * @param issuer a non-null, non-empty issuer string.
     */
    public CreateRequest setIssuer(String issuer) {
      checkArgument(!Strings.isNullOrEmpty(issuer), "issuer must not be null or empty");
      try {
        new URL(issuer);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(issuer + " is a malformed URL", e);
      }
      properties.put("issuer", issuer);
      return this;
    }

    CreateRequest getThis() {
      return this;
    }
  }
}
