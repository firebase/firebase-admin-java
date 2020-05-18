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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.ProviderConfig.AbstractCreateRequest;
import com.google.firebase.auth.ProviderConfig.AbstractUpdateRequest;
import com.google.firebase.auth.internal.SamlProviderConfigResponse.IdpCertificate;
import com.google.firebase.auth.internal.SamlProviderConfigResponse.IdpConfig;
import com.google.firebase.auth.internal.SamlProviderConfigResponse.SpConfig;
import java.util.ArrayList;
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
  private IdpConfig idpConfig;

  @Key("spConfig")
  private SpConfig spConfig;

  public String getIdpEntityId() {
    return idpConfig.getIdpEntityId();
  }

  public String getSsoUrl() {
    return idpConfig.getSsoUrl();
  }

  public List<String> getX509Certificates() {
    ImmutableList.Builder<String> certificates = ImmutableList.<String>builder();
    for (IdpCertificate idpCertificate : idpConfig.getIdpCertificates()) {
      certificates.add(idpCertificate.getX509Certificate());
    }
    return certificates.build();
  }

  public String getRpEntityId() {
    return spConfig.getRpEntityId();
  }

  public String getCallbackUrl() {
    return spConfig.getCallbackUrl();
  }

  static List<Object> ensureNestedList(Map<String, Object> outerMap, String id) {
    List<Object> list = (List<Object>) outerMap.get(id);
    if (list == null) {
      list = new ArrayList<Object>();
      outerMap.put(id, list);
    }
    return list;
  }

  static Map<String, Object> ensureNestedMap(Map<String, Object> outerMap, String id) {
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

    // TODO(micahstairs): Add 'addAllX509Certificates' method.

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

    // TODO(micahstairs): Add 'setRequestSigningEnabled' method.

    CreateRequest getThis() {
      return this;
    }

    void assertValidProviderIdFormat(String providerId) {
      checkArgument(providerId.startsWith("saml."), "Invalid SAML provider ID: " + providerId);
    }
  }
}
