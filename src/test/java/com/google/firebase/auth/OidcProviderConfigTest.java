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
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class OidcProviderConfigTest {

  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();

  private static final String OIDC_JSON_STRING =
      ("{"
        + "  'name':        'projects/projectId/oauthIdpConfigs/oidc.provider-id',"
        + "  'displayName': 'DISPLAY_NAME',"
        + "  'enabled':      true,"
        + "  'clientId':    'CLIENT_ID',"
        + "  'issuer':      'https://oidc.com/issuer'"
        + "}").replace("'", "\"");

  @Test
  public void testJsonDeserialization() throws IOException {
    OidcProviderConfig config = jsonFactory.fromString(OIDC_JSON_STRING, OidcProviderConfig.class);

    assertEquals("oidc.provider-id", config.getProviderId());
    assertEquals("DISPLAY_NAME", config.getDisplayName());
    assertTrue(config.isEnabled());
    assertEquals("CLIENT_ID", config.getClientId());
    assertEquals("https://oidc.com/issuer", config.getIssuer());
  }

  @Test
  public void testCreateRequest() throws IOException {
    OidcProviderConfig.CreateRequest createRequest = new OidcProviderConfig.CreateRequest();
    createRequest
      .setProviderId("oidc.provider-id")
      .setDisplayName("DISPLAY_NAME")
      .setEnabled(false)
      .setClientId("CLIENT_ID")
      .setIssuer("https://oidc.com/issuer");

    assertEquals("oidc.provider-id", createRequest.getProviderId());
    Map<String,Object> properties = createRequest.getProperties();
    assertEquals(properties.size(), 4);
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("enabled"));
    assertEquals("CLIENT_ID", (String) properties.get("clientId"));
    assertEquals("https://oidc.com/issuer", (String) properties.get("issuer"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingProviderId() {
    new OidcProviderConfig.CreateRequest().setProviderId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestInvalidProviderId() {
    new OidcProviderConfig.CreateRequest().setProviderId("saml.provider-id");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingDisplayName() {
    new OidcProviderConfig.CreateRequest().setDisplayName(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingClientId() {
    new OidcProviderConfig.CreateRequest().setClientId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestMissingIssuer() {
    new OidcProviderConfig.CreateRequest().setIssuer(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRequestInvalidIssuerUrl() {
    new OidcProviderConfig.CreateRequest().setIssuer("not a valid url");
  }

  @Test
  public void testUpdateRequestFromOidcProviderConfig() throws IOException {
    OidcProviderConfig config = jsonFactory.fromString(OIDC_JSON_STRING, OidcProviderConfig.class);

    OidcProviderConfig.UpdateRequest updateRequest = config.updateRequest();

    assertEquals("oidc.provider-id", updateRequest.getProviderId());
    assertTrue(updateRequest.getProperties().isEmpty());
  }

  @Test
  public void testUpdateRequest() throws IOException {
    OidcProviderConfig.UpdateRequest updateRequest =
        new OidcProviderConfig.UpdateRequest("oidc.provider-id");
    updateRequest
      .setDisplayName("DISPLAY_NAME")
      .setEnabled(false)
      .setClientId("CLIENT_ID")
      .setIssuer("https://oidc.com/issuer");

    assertEquals("oidc.provider-id", updateRequest.getProviderId());
    Map<String,Object> properties = updateRequest.getProperties();
    assertEquals(properties.size(), 4);
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("enabled"));
    assertEquals("CLIENT_ID", (String) properties.get("clientId"));
    assertEquals("https://oidc.com/issuer", (String) properties.get("issuer"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingProviderId() {
    new OidcProviderConfig.UpdateRequest(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestInvalidProviderId() {
    new OidcProviderConfig.UpdateRequest("saml.provider-id");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingDisplayName() {
    new OidcProviderConfig.UpdateRequest("oidc.provider-id").setDisplayName(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingClientId() {
    new OidcProviderConfig.UpdateRequest("oidc.provider-id").setClientId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestMissingIssuer() {
    new OidcProviderConfig.UpdateRequest("oidc.provider-id").setIssuer(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateRequestInvalidIssuerUrl() {
    new OidcProviderConfig.UpdateRequest("oidc.provider-id").setIssuer("not a valid url");
  }
}
