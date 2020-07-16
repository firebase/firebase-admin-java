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

package com.google.firebase.auth.multitenancy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public class TenantTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  private static final String TENANT_JSON_STRING = 
      "{"
        + "\"name\":\"projects/project-id/resource/TENANT_ID\","
        + "\"displayName\":\"DISPLAY_NAME\","
        + "\"allowPasswordSignup\":true,"
        + "\"enableEmailLinkSignin\":false"
        + "}";

  @Test
  public void testJsonDeserialization() throws IOException {
    Tenant tenant = JSON_FACTORY.fromString(TENANT_JSON_STRING, Tenant.class);

    assertEquals(tenant.getTenantId(), "TENANT_ID");
    assertEquals(tenant.getDisplayName(), "DISPLAY_NAME");
    assertTrue(tenant.isPasswordSignInAllowed());
    assertFalse(tenant.isEmailLinkSignInEnabled());
  }

  @Test
  public void testUpdateRequestFromTenant() throws IOException {
    Tenant tenant = JSON_FACTORY.fromString(TENANT_JSON_STRING, Tenant.class);

    Tenant.UpdateRequest updateRequest = tenant.updateRequest();

    assertEquals("TENANT_ID", updateRequest.getTenantId());
    assertTrue(updateRequest.getProperties().isEmpty());
  }

  @Test
  public void testUpdateRequestFromTenantId() throws IOException {
    Tenant.UpdateRequest updateRequest = new Tenant.UpdateRequest("TENANT_ID");
    updateRequest
      .setDisplayName("DISPLAY_NAME")
      .setPasswordSignInAllowed(false)
      .setEmailLinkSignInEnabled(true);

    assertEquals("TENANT_ID", updateRequest.getTenantId());
    Map<String,Object> properties = updateRequest.getProperties();
    assertEquals(properties.size(), 3);
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("allowPasswordSignup"));
    assertTrue((boolean) properties.get("enableEmailLinkSignin"));
  }

  @Test
  public void testCreateRequest() throws IOException {
    Tenant.CreateRequest createRequest = new Tenant.CreateRequest();
    createRequest
      .setDisplayName("DISPLAY_NAME")
      .setPasswordSignInAllowed(false)
      .setEmailLinkSignInEnabled(true);

    Map<String,Object> properties = createRequest.getProperties();
    assertEquals(properties.size(), 3);
    assertEquals("DISPLAY_NAME", (String) properties.get("displayName"));
    assertFalse((boolean) properties.get("allowPasswordSignup"));
    assertTrue((boolean) properties.get("enableEmailLinkSignin"));
  }
}

