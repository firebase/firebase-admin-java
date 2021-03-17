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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains metadata associated with a SAML Auth provider.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class SamlProviderConfig extends ProviderConfig {

  @Key("idpConfig")
  private GenericJson idpConfig;

  @Key("spConfig")
  private GenericJson spConfig;

  public String getIdpEntityId() {
    return (String) idpConfig.get("idpEntityId");
  }

  public String getSsoUrl() {
    return (String) idpConfig.get("ssoUrl");
  }

  public List<String> getX509Certificates() {
    List<Map<String, String>> idpCertificates =
        (List<Map<String, String>>) idpConfig.get("idpCertificates");
    checkNotNull(idpCertificates);
    ImmutableList.Builder<String> certificates = ImmutableList.<String>builder();
    for (Map<String, String> idpCertificate : idpCertificates) {
      certificates.add(idpCertificate.get("x509Certificate"));
    }
    return certificates.build();
  }

  public String getRpEntityId() {
    return (String) spConfig.get("spEntityId");
  }

  public String getCallbackUrl() {
    return (String) spConfig.get("callbackUri");
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

  static void checkSamlProviderId(String providerId) {
    checkArgument(!Strings.isNullOrEmpty(providerId), "Provider ID must not be null or empty.");
    checkArgument(providerId.startsWith("saml."),
        "Invalid SAML provider ID (must be prefixed with 'saml.'): " + providerId);
  }

  private static List<Object> ensureNestedList(Map<String, Object> outerMap, String id) {
    List<Object> list = (List<Object>) outerMap.get(id);
    if (list == null) {
      list = new ArrayList<Object>();
      outerMap.put(id, list);
    }
    return list;
  }

  private static Map<String, Object> ensureNestedMap(Map<String, Object> outerMap, String id) {
    Map<String, Object> map = (Map<String, Object>) outerMap.get(id);
    if (map == null) {
      map = new HashMap<String, Object>();
      outerMap.put(id, map);
    }
    return map;
  }

  /**
   * A specification class for creating a new SAML Auth provider.
   *
   * <p>Set the initial attributes of the new provider by calling various setter methods available
   * in this class.
   */
  public static final class CreateRequest extends AbstractCreateRequest<CreateRequest> {

    /**
     * Creates a new {@link CreateRequest}, which can be used to create a new SAML Auth provider.
     *
     * <p>The returned object should be passed to
     * {@link AbstractFirebaseAuth#createSamlProviderConfig(CreateRequest)} to register the provider
     * information persistently.
     */
    public CreateRequest() { }

    /**
     * Sets the ID for the new provider.
     *
     * @param providerId A non-null, non-empty provider ID string.
     * @throws IllegalArgumentException If the provider ID is null or empty, or is not prefixed with
     *     'saml.'.
     */
    @Override
    public CreateRequest setProviderId(String providerId) {
      checkSamlProviderId(providerId);
      return super.setProviderId(providerId);
    }

    /**
     * Sets the IDP entity ID for the new provider.
     *
     * @param idpEntityId A non-null, non-empty IDP entity ID string.
     * @throws IllegalArgumentException If the IDP entity ID is null or empty.
     */
    public CreateRequest setIdpEntityId(String idpEntityId) {
      checkArgument(!Strings.isNullOrEmpty(idpEntityId),
          "IDP entity ID must not be null or empty.");
      ensureNestedMap(properties, "idpConfig").put("idpEntityId", idpEntityId);
      return this;
    }

    /**
     * Sets the SSO URL for the new provider.
     *
     * @param ssoUrl A non-null, non-empty SSO URL string.
     * @throws IllegalArgumentException If the SSO URL is null or empty, or if the format is
     *     invalid.
     */
    public CreateRequest setSsoUrl(String ssoUrl) {
      checkArgument(!Strings.isNullOrEmpty(ssoUrl), "SSO URL must not be null or empty.");
      assertValidUrl(ssoUrl);
      ensureNestedMap(properties, "idpConfig").put("ssoUrl", ssoUrl);
      return this;
    }

    /**
     * Adds a x509 certificate to the new provider.
     *
     * @param x509Certificate A non-null, non-empty x509 certificate string.
     * @throws IllegalArgumentException If the x509 certificate is null or empty.
     */
    public CreateRequest addX509Certificate(String x509Certificate) {
      checkArgument(!Strings.isNullOrEmpty(x509Certificate),
          "The x509 certificate must not be null or empty.");
      Map<String, Object> idpConfigProperties = ensureNestedMap(properties, "idpConfig");
      List<Object> x509Certificates = ensureNestedList(idpConfigProperties, "idpCertificates");
      x509Certificates.add(ImmutableMap.<String, Object>of("x509Certificate", x509Certificate));
      return this;
    }

    /**
     * Adds a collection of x509 certificates to the new provider.
     *
     * @param x509Certificates A non-null, non-empty collection of x509 certificate strings.
     * @throws IllegalArgumentException If the collection is null or empty, or if any x509
     *     certificates are null or empty.
     */
    public CreateRequest addAllX509Certificates(Collection<String> x509Certificates) {
      checkArgument(x509Certificates != null,
          "The collection of x509 certificates must not be null.");
      checkArgument(!x509Certificates.isEmpty(),
          "The collection of x509 certificates must not be empty.");
      for (String certificate : x509Certificates) {
        addX509Certificate(certificate);
      }
      return this;
    }

    /**
     * Sets the RP entity ID for the new provider.
     *
     * @param rpEntityId A non-null, non-empty RP entity ID string.
     * @throws IllegalArgumentException If the RP entity ID is null or empty.
     */
    public CreateRequest setRpEntityId(String rpEntityId) {
      checkArgument(!Strings.isNullOrEmpty(rpEntityId), "RP entity ID must not be null or empty.");
      ensureNestedMap(properties, "spConfig").put("spEntityId", rpEntityId);
      return this;
    }

    /**
     * Sets the callback URL for the new provider.
     *
     * @param callbackUrl A non-null, non-empty callback URL string.
     * @throws IllegalArgumentException If the callback URL is null or empty, or if the format is
     *     invalid.
     */
    public CreateRequest setCallbackUrl(String callbackUrl) {
      checkArgument(!Strings.isNullOrEmpty(callbackUrl), "Callback URL must not be null or empty.");
      assertValidUrl(callbackUrl);
      ensureNestedMap(properties, "spConfig").put("callbackUri", callbackUrl);
      return this;
    }

    CreateRequest getThis() {
      return this;
    }
  }

  /**
   * A specification class for updating an existing SAML Auth provider.
   *
   * <p>An instance of this class can be obtained via a {@link SamlProviderConfig} object, or from
   * a provider ID string. Specify the changes to be made to the provider config by calling the
   * various setter methods available in this class.
   */
  public static final class UpdateRequest extends AbstractUpdateRequest<UpdateRequest> {
    /**
     * Creates a new {@link UpdateRequest}, which can be used to updates an existing SAML Auth
     * provider.
     *
     * <p>The returned object should be passed to
     * {@link AbstractFirebaseAuth#updateSamlProviderConfig(UpdateRequest)} to update the provider
     * information persistently.
     *
     * @param providerId a non-null, non-empty provider ID string.
     * @throws IllegalArgumentException If the provider ID is null or empty, or is not prefixed with
     *     'saml.'.
     */
    public UpdateRequest(String providerId) {
      super(providerId);
      checkSamlProviderId(providerId);
    }

    /**
     * Sets the IDP entity ID for the existing provider.
     *
     * @param idpEntityId A non-null, non-empty IDP entity ID string.
     * @throws IllegalArgumentException If the IDP entity ID is null or empty.
     */
    public UpdateRequest setIdpEntityId(String idpEntityId) {
      checkArgument(!Strings.isNullOrEmpty(idpEntityId),
          "IDP entity ID must not be null or empty.");
      ensureNestedMap(properties, "idpConfig").put("idpEntityId", idpEntityId);
      return this;
    }

    /**
     * Sets the SSO URL for the existing provider.
     *
     * @param ssoUrl A non-null, non-empty SSO URL string.
     * @throws IllegalArgumentException If the SSO URL is null or empty, or if the format is
     *     invalid.
     */
    public UpdateRequest setSsoUrl(String ssoUrl) {
      checkArgument(!Strings.isNullOrEmpty(ssoUrl), "SSO URL must not be null or empty.");
      assertValidUrl(ssoUrl);
      ensureNestedMap(properties, "idpConfig").put("ssoUrl", ssoUrl);
      return this;
    }

    /**
     * Adds a x509 certificate to the existing provider.
     *
     * @param x509Certificate A non-null, non-empty x509 certificate string.
     * @throws IllegalArgumentException If the x509 certificate is null or empty.
     */
    public UpdateRequest addX509Certificate(String x509Certificate) {
      checkArgument(!Strings.isNullOrEmpty(x509Certificate),
          "The x509 certificate must not be null or empty.");
      Map<String, Object> idpConfigProperties = ensureNestedMap(properties, "idpConfig");
      List<Object> x509Certificates = ensureNestedList(idpConfigProperties, "idpCertificates");
      x509Certificates.add(ImmutableMap.<String, Object>of("x509Certificate", x509Certificate));
      return this;
    }

    /**
     * Adds a collection of x509 certificates to the existing provider.
     *
     * @param x509Certificates A non-null, non-empty collection of x509 certificate strings.
     * @throws IllegalArgumentException If the collection is null or empty, or if any x509
     *     certificates are null or empty.
     */
    public UpdateRequest addAllX509Certificates(Collection<String> x509Certificates) {
      checkArgument(x509Certificates != null,
          "The collection of x509 certificates must not be null.");
      checkArgument(!x509Certificates.isEmpty(),
          "The collection of x509 certificates must not be empty.");
      for (String certificate : x509Certificates) {
        addX509Certificate(certificate);
      }
      return this;
    }

    /**
     * Sets the RP entity ID for the existing provider.
     *
     * @param rpEntityId A non-null, non-empty RP entity ID string.
     * @throws IllegalArgumentException If the RP entity ID is null or empty.
     */
    public UpdateRequest setRpEntityId(String rpEntityId) {
      checkArgument(!Strings.isNullOrEmpty(rpEntityId), "RP entity ID must not be null or empty.");
      ensureNestedMap(properties, "spConfig").put("spEntityId", rpEntityId);
      return this;
    }

    /**
     * Sets the callback URL for the exising provider.
     *
     * @param callbackUrl A non-null, non-empty callback URL string.
     * @throws IllegalArgumentException If the callback URL is null or empty, or if the format is
     *     invalid.
     */
    public UpdateRequest setCallbackUrl(String callbackUrl) {
      checkArgument(!Strings.isNullOrEmpty(callbackUrl), "Callback URL must not be null or empty.");
      assertValidUrl(callbackUrl);
      ensureNestedMap(properties, "spConfig").put("callbackUri", callbackUrl);
      return this;
    }

    UpdateRequest getThis() {
      return this;
    }
  }
}
