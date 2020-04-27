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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class OidcProviderConfigTest {

  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();

  private static final String OIDC_JSON_STRING =
      "{"
        + "\"name\":\"projects/projectId/oauthIdpConfigs/oidc.provider-id\","
        + "\"displayName\":\"DISPLAY_NAME\","
        + "\"enabled\":true,"
        + "\"clientId\":\"CLIENT_ID\","
        + "\"issuer\":\"https://oidc.com/issuer\""
        + "}";

  @Test
  public void testJsonSerialization() throws IOException {
    OidcProviderConfig config = jsonFactory.fromString(OIDC_JSON_STRING, OidcProviderConfig.class);

    assertEquals(config.getProviderId(), "oidc.provider-id");
    assertEquals(config.getDisplayName(), "DISPLAY_NAME");
    assertTrue(config.isEnabled());
    assertEquals(config.getClientId(), "CLIENT_ID");
    assertEquals(config.getIssuer(), "https://oidc.com/issuer");
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
  public void testInvalidIssuerUrl() {
    new OidcProviderConfig.CreateRequest().setIssuer("not a valid url");
  }
}
