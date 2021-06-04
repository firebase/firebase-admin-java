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

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains metadata associated with an OIDC Auth provider.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class OidcProviderConfig extends ProviderConfig {

  @Key("clientId")
  private String clientId;

  @Key("clientSecret")
  private String clientSecret;

  @Key("issuer")
  private String issuer;

  @Key("responseType")
  private GenericJson responseType;

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getIssuer() {
    return issuer;
  }

  public boolean isCodeResponseType() {
    return (responseType.containsKey("code") && (boolean) responseType.get("code"));
  }

  public boolean isIdTokenResponseType() {
    return (responseType.containsKey("idToken") && (boolean) responseType.get("idToken"));
  }

  /**
   * Returns a new {@link UpdateRequest}, which can be used to update the attributes of this
   * provider config.
   *
   * @return A non-null {@link UpdateRequest} instance.
   */
  public UpdateRequest updateRequest() {
    return new UpdateRequest(getProviderId());
  }

  static void checkOidcProviderId(String providerId) {
    checkArgument(!Strings.isNullOrEmpty(providerId), "Provider ID must not be null or empty.");
    checkArgument(providerId.startsWith("oidc."),
        "Invalid OIDC provider ID (must be prefixed with 'oidc.'): " + providerId);
  }

  static Map<String, Boolean> ensureResponseType(Map<String,Object> properties) {
    if (properties.get("responseType") == null) {
      properties.put("responseType", new HashMap<String, Boolean>());
    }
    return (Map<String, Boolean>) properties.get("responseType");
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
     * {@link AbstractFirebaseAuth#createOidcProviderConfig(CreateRequest)} to save the config.
     */
    public CreateRequest() { }

    /**
     * Sets the ID for the new provider.
     *
     * @param providerId A non-null, non-empty provider ID string.
     * @throws IllegalArgumentException If the provider ID is null or empty, or is not prefixed with
     *     'oidc.'.
     */
    @Override
    public CreateRequest setProviderId(String providerId) {
      checkOidcProviderId(providerId);
      return super.setProviderId(providerId);
    }

    /**
     * Sets the client ID for the new provider.
     *
     * @param clientId A non-null, non-empty client ID string.
     * @throws IllegalArgumentException If the client ID is null or empty.
     */
    public CreateRequest setClientId(String clientId) {
      checkArgument(!Strings.isNullOrEmpty(clientId), "Client ID must not be null or empty.");
      properties.put("clientId", clientId);
      return this;
    }

    /**
     * Sets the client secret for the new provider. This is required for the code flow.
     *
     * @param clientSecret A non-null, non-empty client secret string.
     * @throws IllegalArgumentException If the client secret is null or empty.
     */
    public CreateRequest setClientSecret(String clientSecret) {
      checkArgument(!Strings.isNullOrEmpty(clientSecret),
          "Client Secret must not be null or empty.");
      properties.put("clientSecret", clientSecret);
      return this;
    }

    /**
     * Sets the issuer for the new provider.
     *
     * @param issuer A non-null, non-empty issuer URL string.
     * @throws IllegalArgumentException If the issuer URL is null or empty, or if the format is
     *     invalid.
     */
    public CreateRequest setIssuer(String issuer) {
      checkArgument(!Strings.isNullOrEmpty(issuer), "Issuer must not be null or empty.");
      assertValidUrl(issuer);
      properties.put("issuer", issuer);
      return this;
    }

    /**
     * Sets whether to enable the code response flow for the new provider. By default, this is not
     * enabled if no response type is specified.
     *
     * <p>A client secret must be set for this response type.
     *
     * <p>Having both the code and ID token response flows is currently not supported.
     *
     * @param enabled A boolean signifying whether the code response type is supported.
     */
    public CreateRequest setCodeResponseType(boolean enabled) {
      Map<String, Boolean> map = ensureResponseType(properties);
      map.put("code", enabled);
      return this;
    }

    /**
     * Sets whether to enable the ID token response flow for the new provider. By default, this is
     * enabled if no response type is specified.
     *
     * <p>Having both the code and ID token response flows is currently not supported.
     *
     * @param enabled A boolean signifying whether the ID token response type is supported.
     */
    public CreateRequest setIdTokenResponseType(boolean enabled) {
      Map<String, Boolean> map = ensureResponseType(properties);
      map.put("idToken", enabled);
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
     * {@link AbstractFirebaseAuth#updateOidcProviderConfig(UpdateRequest)} to save the updated
     * config.
     *
     * @param providerId A non-null, non-empty provider ID string.
     * @throws IllegalArgumentException If the provider ID is null or empty, or is not prefixed with
     *     "oidc.".
     */
    public UpdateRequest(String providerId) {
      super(providerId);
      checkOidcProviderId(providerId);
    }

    /**
     * Sets the client ID for the exsting provider.
     *
     * @param clientId A non-null, non-empty client ID string.
     * @throws IllegalArgumentException If the client ID is null or empty.
     */
    public UpdateRequest setClientId(String clientId) {
      checkArgument(!Strings.isNullOrEmpty(clientId), "Client ID must not be null or empty.");
      properties.put("clientId", clientId);
      return this;
    }

    /**
     * Sets the client secret for the new provider. This is required for the code flow.
     *
     * @param clientSecret A non-null, non-empty client secret string.
     * @throws IllegalArgumentException If the client secret is null or empty.
     */
    public UpdateRequest setClientSecret(String clientSecret) {
      checkArgument(!Strings.isNullOrEmpty(clientSecret),
          "Client Secret must not be null or empty.");
      properties.put("clientSecret", clientSecret);
      return this;
    }

    /**
     * Sets the issuer for the existing provider.
     *
     * @param issuer A non-null, non-empty issuer URL string.
     * @throws IllegalArgumentException If the issuer URL is null or empty, or if the format is
     *     invalid.
     */
    public UpdateRequest setIssuer(String issuer) {
      checkArgument(!Strings.isNullOrEmpty(issuer), "Issuer must not be null or empty.");
      assertValidUrl(issuer);
      properties.put("issuer", issuer);
      return this;
    }

    /**
     * Sets whether to enable the code response flow for the new provider. By default, this is not
     * enabled if no response type is specified.
     *
     * <p>A client secret must be set for this response type.
     *
     * <p>Having both the code and ID token response flows is currently not supported.
     *
     * @param enabled A boolean signifying whether the code response type is supported.
     */
    public UpdateRequest setCodeResponseType(boolean enabled) {
      Map<String, Boolean> map = ensureResponseType(properties);
      map.put("code", enabled);
      return this;
    }

    /**
     * Sets whether to enable the ID token response flow for the new provider. By default, this is
     * enabled if no response type is specified.
     *
     * <p>Having both the code and ID token response flows is currently not supported.
     *
     * @param enabled A boolean signifying whether the ID token response type is supported.
     */
    public UpdateRequest setIdTokenResponseType(boolean enabled) {
      Map<String, Boolean> map = ensureResponseType(properties);
      map.put("idToken", enabled);
      return this;
    }

    UpdateRequest getThis() {
      return this;
    }
  }
}
