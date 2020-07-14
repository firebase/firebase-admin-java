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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SamlProviderConfigTest {

  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();

  private static final String SAML_JSON_STRING =
      ("{"
        + "  'name':        'projects/projectId/inboundSamlConfigs/saml.provider-id',"
        + "  'displayName': 'DISPLAY_NAME',"
        + "  'enabled':      true,"
        + "  'idpConfig': {"
        + "    'idpEntityId': 'IDP_ENTITY_ID',"
        + "    'ssoUrl':      'https://example.com/login',"
        + "    'idpCertificates': ["
        + "      { 'x509Certificate': 'certificate1' },"
        + "      { 'x509Certificate': 'certificate2' }"
        + "    ]"
        + "  },"
        + "  'spConfig': {"
        + "    'spEntityId':  'RP_ENTITY_ID',"
        + "    'callbackUri': 'https://projectId.firebaseapp.com/__/auth/handler'"
        + "   }"
        + "}").replace("'", "\"");

  @Test
  public void testJsonDeserialization() throws IOException {
    SamlProviderConfig config = jsonFactory.fromString(SAML_JSON_STRING, SamlProviderConfig.class);

    assertEquals("saml.provider-id", config.getProviderId());
    assertEquals("DISPLAY_NAME", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("IDP_ENTITY_ID", config.getIdpEntityId());
    assertEquals("https://example.com/login", config.getSsoUrl());
    assertEquals(ImmutableList.of("certificate1", "certificate2"), config.getX509Certificates());
    assertEquals("RP_ENTITY_ID", config.getRpEntityId());
    assertEquals("https://projectId.firebaseapp.com/__/auth/handler", config.getCallbackUrl());
  }

  @Test
  public void testCreateRequest() throws IOException {
    SamlProviderConfig.CreateRequest createRequest =
        new SamlProviderConfig.CreateRequest()
          .setProviderId("saml.provider-id")
          .setDisplayName("DISPLAY_NAME")
          .setEnabled(false)
          .setIdpEntityId("IDP_ENTITY_ID")
          .setSsoUrl("https://example.com/login")
          .addX509Certificate("certificate1")
          .addX509Certificate("certificate2")
          .setRpEntityId("RP_ENTITY_ID")
          .setCallbackUrl("https://projectId.firebaseapp.com/__/auth/handler");

    assertEquals("saml.provider-id", createRequest.getProviderId());
    Map<String,Object> properties = createRequest.getProperties();
    assertEquals(4, properties.size());
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("enabled"));

    Map<String, Object> idpConfig = (Map<String, Object>) properties.get("idpConfig");
    assertNotNull(idpConfig);
    assertEquals(3, idpConfig.size());
    assertEquals("IDP_ENTITY_ID", idpConfig.get("idpEntityId"));
    assertEquals("https://example.com/login", idpConfig.get("ssoUrl"));
    List<Object> idpCertificates = (List<Object>) idpConfig.get("idpCertificates");
    assertNotNull(idpCertificates);
    assertEquals(2, idpCertificates.size());
    assertEquals(ImmutableMap.of("x509Certificate", "certificate1"), idpCertificates.get(0));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate2"), idpCertificates.get(1));

    Map<String, Object> spConfig = (Map<String, Object>) properties.get("spConfig");
    assertNotNull(spConfig);
    assertEquals(2, spConfig.size());
    assertEquals("RP_ENTITY_ID", spConfig.get("spEntityId"));
    assertEquals("https://projectId.firebaseapp.com/__/auth/handler", spConfig.get("callbackUri"));
  }

  @Test
  public void testCreateRequestX509Certificates() throws IOException {
    SamlProviderConfig.CreateRequest createRequest =
        new SamlProviderConfig.CreateRequest()
          .addX509Certificate("certificate1")
          .addAllX509Certificates(ImmutableList.of("certificate2", "certificate3"))
          .addX509Certificate("certificate4");

    Map<String,Object> properties = createRequest.getProperties();
    assertEquals(1, properties.size());
    Map<String, Object> idpConfig = (Map<String, Object>) properties.get("idpConfig");
    assertNotNull(idpConfig);
    assertEquals(1, idpConfig.size());

    List<Object> idpCertificates = (List<Object>) idpConfig.get("idpCertificates");
    assertNotNull(idpCertificates);
    assertEquals(4, idpCertificates.size());
    assertEquals(ImmutableMap.of("x509Certificate", "certificate1"), idpCertificates.get(0));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate2"), idpCertificates.get(1));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate3"), idpCertificates.get(2));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate4"), idpCertificates.get(3));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingProviderId() {
    new SamlProviderConfig.CreateRequest().setProviderId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestInvalidProviderId() {
    new SamlProviderConfig.CreateRequest().setProviderId("oidc.provider-id");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingDisplayName() {
    new SamlProviderConfig.CreateRequest().setDisplayName(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingIdpEntityId() {
    new SamlProviderConfig.CreateRequest().setIdpEntityId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingSsoUrl() {
    new SamlProviderConfig.CreateRequest().setSsoUrl(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestInvalidSsoUrl() {
    new SamlProviderConfig.CreateRequest().setSsoUrl("not a valid url");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingX509Certificate() {
    new SamlProviderConfig.CreateRequest().addX509Certificate(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestNullX509CertificatesCollection() {
    new SamlProviderConfig.CreateRequest().addAllX509Certificates(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestEmptyX509CertificatesCollection() {
    new SamlProviderConfig.CreateRequest().addAllX509Certificates(ImmutableList.<String>of());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingRpEntityId() {
    new SamlProviderConfig.CreateRequest().setRpEntityId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingCallbackUrl() {
    new SamlProviderConfig.CreateRequest().setCallbackUrl(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestInvalidCallbackUrl() {
    new SamlProviderConfig.CreateRequest().setCallbackUrl("not a valid url");
  }

  @Test
  public void testUpdateRequestFromSamlProviderConfig() throws IOException {
    SamlProviderConfig config = jsonFactory.fromString(SAML_JSON_STRING, SamlProviderConfig.class);

    SamlProviderConfig.UpdateRequest updateRequest = config.updateRequest();

    assertEquals("saml.provider-id", updateRequest.getProviderId());
    assertTrue(updateRequest.getProperties().isEmpty());
  }

  @Test
  public void testUpdateRequest() throws IOException {
    SamlProviderConfig.UpdateRequest updateRequest =
        new SamlProviderConfig.UpdateRequest("saml.provider-id");
    updateRequest
      .setDisplayName("DISPLAY_NAME")
      .setEnabled(false)
      .setIdpEntityId("IDP_ENTITY_ID")
      .setSsoUrl("https://example.com/login")
      .addX509Certificate("certificate1")
      .addX509Certificate("certificate2")
      .setRpEntityId("RP_ENTITY_ID")
      .setCallbackUrl("https://projectId.firebaseapp.com/__/auth/handler");

    Map<String,Object> properties = updateRequest.getProperties();
    assertEquals(4, properties.size());
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("enabled"));

    Map<String, Object> idpConfig = (Map<String, Object>) properties.get("idpConfig");
    assertNotNull(idpConfig);
    assertEquals(3, idpConfig.size());
    assertEquals("IDP_ENTITY_ID", idpConfig.get("idpEntityId"));
    assertEquals("https://example.com/login", idpConfig.get("ssoUrl"));
    List<Object> idpCertificates = (List<Object>) idpConfig.get("idpCertificates");
    assertNotNull(idpCertificates);
    assertEquals(2, idpCertificates.size());
    assertEquals(ImmutableMap.of("x509Certificate", "certificate1"), idpCertificates.get(0));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate2"), idpCertificates.get(1));

    Map<String, Object> spConfig = (Map<String, Object>) properties.get("spConfig");
    assertNotNull(spConfig);
    assertEquals(2, spConfig.size());
    assertEquals("RP_ENTITY_ID", spConfig.get("spEntityId"));
    assertEquals("https://projectId.firebaseapp.com/__/auth/handler", spConfig.get("callbackUri"));
  }

  @Test
  public void testUpdateRequestX509Certificates() throws IOException {
    SamlProviderConfig.UpdateRequest updateRequest =
        new SamlProviderConfig.UpdateRequest("saml.provider-id");
    updateRequest
      .addX509Certificate("certificate1")
      .addAllX509Certificates(ImmutableList.of("certificate2", "certificate3"))
      .addX509Certificate("certificate4");

    Map<String,Object> properties = updateRequest.getProperties();
    assertEquals(1, properties.size());
    Map<String, Object> idpConfig = (Map<String, Object>) properties.get("idpConfig");
    assertNotNull(idpConfig);
    assertEquals(1, idpConfig.size());

    List<Object> idpCertificates = (List<Object>) idpConfig.get("idpCertificates");
    assertNotNull(idpCertificates);
    assertEquals(4, idpCertificates.size());
    assertEquals(ImmutableMap.of("x509Certificate", "certificate1"), idpCertificates.get(0));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate2"), idpCertificates.get(1));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate3"), idpCertificates.get(2));
    assertEquals(ImmutableMap.of("x509Certificate", "certificate4"), idpCertificates.get(3));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingProviderId() {
    new SamlProviderConfig.UpdateRequest(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestInvalidProviderId() {
    new SamlProviderConfig.UpdateRequest("oidc.invalid-saml-provider-id");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingDisplayName() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setDisplayName(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingIdpEntityId() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setIdpEntityId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingSsoUrl() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setSsoUrl(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestInvalidSsoUrl() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setSsoUrl("not a valid url");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingX509Certificate() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").addX509Certificate(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestNullX509CertificatesCollection() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").addAllX509Certificates(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestEmptyX509CertificatesCollection() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id")
        .addAllX509Certificates(ImmutableList.<String>of());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingRpEntityId() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setRpEntityId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingCallbackUrl() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setCallbackUrl(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestInvalidCallbackUrl() {
    new SamlProviderConfig.UpdateRequest("saml.provider-id").setCallbackUrl("not a valid url");
  }
}
