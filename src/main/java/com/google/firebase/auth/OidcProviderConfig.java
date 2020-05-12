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
import com.google.firebase.auth.ProviderConfig.AbstractCreateRequest;
import com.google.firebase.auth.ProviderConfig.AbstractUpdateRequest;

/**
 * Contains metadata associated with an OIDC Auth provider.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class OidcProviderConfig extends ProviderConfig {

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
   * Returns a new {@link UpdateRequest}, which can be used to update the attributes of this
   * provider config.
   *
   * @return a non-null {@link UpdateRequest} instance.
   */
  public UpdateRequest updateRequest() {
    return new UpdateRequest(getProviderId());
  }

  /**
   * A specification class for creating a new OIDC Auth provider.
   *
   * <p>Set the initial attributes of the new provider by calling various setter methods available
   * in this class.
   */
  public static final class CreateRequest extends AbstractCreateRequest<CreateRequest> {

    /**
     * Creates a new {@link CreateRequest}, which can be used to create a new OIDC Auth provider.
     *
     * <p>The returned object should be passed to
     * {@link AbstractFirebaseAuth#createOidcProviderConfig(CreateRequest)} to register the provider
     * information persistently.
     */
    public CreateRequest() { }

    /**
     * Sets the client ID for the new provider.
     *
     * @param clientId a non-null, non-empty client ID string.
     */
    public CreateRequest setClientId(String clientId) {
      checkArgument(!Strings.isNullOrEmpty(clientId), "Client ID must not be null or empty.");
      properties.put("clientId", clientId);
      return this;
    }

    /**
     * Sets the issuer for the new provider.
     *
     * @param issuer a non-null, non-empty issuer URL string.
     */
    public CreateRequest setIssuer(String issuer) {
      checkArgument(!Strings.isNullOrEmpty(issuer), "Issuer must not be null or empty.");
      assertValidUrl(issuer);
      properties.put("issuer", issuer);
      return this;
    }

    CreateRequest getThis() {
      return this;
    }
  }

  /**
   * A specification class for updating an existing OIDC Auth provider.
   *
   * <p>An instance of this class can be obtained via a {@link OidcProviderConfig} object, or from
   * a provider ID string. Specify the changes to be made to the provider config by calling the
   * various setter methods available in this class.
   */
  public static final class UpdateRequest extends AbstractUpdateRequest<UpdateRequest> {

    /**
     * Creates a new {@link UpdateRequest}, which can be used to updates an existing OIDC Auth
     * provider.
     *
     * <p>The returned object should be passed to
     * {@link AbstractFirebaseAuth#updateOidcProviderConfig(CreateRequest)} to update the provider
     * information persistently.
     *
     * @param tenantId a non-null, non-empty provider ID string.
     * @throws IllegalArgumentException If the provider ID is null or empty, or if it's an invalid
     *     format
     */
    public UpdateRequest(String providerId) {
      super(providerId);
      checkArgument(providerId.startsWith("oidc."), "Invalid OIDC provider ID: " + providerId);
    }

    /**
     * Sets the client ID for the exsting provider.
     *
     * @param clientId a non-null, non-empty client ID string.
     */
    public UpdateRequest setClientId(String clientId) {
      checkArgument(!Strings.isNullOrEmpty(clientId), "Client ID must not be null or empty.");
      properties.put("clientId", clientId);
      return this;
    }

    /**
     * Sets the issuer for the existing provider.
     *
     * @param issuer a non-null, non-empty issuer URL string.
     */
    public UpdateRequest setIssuer(String issuer) {
      checkArgument(!Strings.isNullOrEmpty(issuer), "Issuer must not be null or empty.");
      assertValidUrl(issuer);
      properties.put("issuer", issuer);
      return this;
    }

    UpdateRequest getThis() {
      return this;
    }
  }
}
