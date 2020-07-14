/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.auth.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.OidcProviderConfig;

import org.junit.Test;

public class ListOidcProviderConfigsResponseTest {

  @Test
  public void testDefaultValues() throws Exception {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse();

    assertEquals(0, response.getProviderConfigs().size());
    assertFalse(response.hasProviderConfigs());
    assertEquals("", response.getPageToken());
  }

  @Test
  public void testEmptyTenantList() throws Exception {
    ListOidcProviderConfigsResponse response =
        new ListOidcProviderConfigsResponse(ImmutableList.<OidcProviderConfig>of(), "PAGE_TOKEN");

    assertEquals(0, response.getProviderConfigs().size());
    assertFalse(response.hasProviderConfigs());
  }

  @Test
  public void testDeserialization() throws Exception {
    String singleQuotedJson = "{"
        + "  'oauthIdpConfigs': ["
        + "    {"   
        + "      'name': 'projects/projectId/oauthIdpConfigs/oidc.provider-id-1'"
        + "    },"
        + "    {"
        + "      'name': 'projects/projectId/oauthIdpConfigs/oidc.provider-id-2'"
        + "    }"
        + "  ],"
        + "  'nextPageToken': 'PAGE_TOKEN'"
        + "}";
    String doubleQuotedJson = replaceSingleQuotesWithDoubleQuotes(singleQuotedJson);
    ListOidcProviderConfigsResponse response = Utils.getDefaultJsonFactory().fromString(
          doubleQuotedJson, ListOidcProviderConfigsResponse.class);

    assertEquals(2, response.getProviderConfigs().size());
    assertEquals("oidc.provider-id-1", response.getProviderConfigs().get(0).getProviderId());
    assertEquals("oidc.provider-id-2", response.getProviderConfigs().get(1).getProviderId());
    assertTrue(response.hasProviderConfigs());
    assertEquals("PAGE_TOKEN", response.getPageToken());
  }

  private static String replaceSingleQuotesWithDoubleQuotes(String str) {
    return str.replace("'", "\"");
  }
}
