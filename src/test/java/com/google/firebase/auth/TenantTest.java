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

package com.google.firebase.auth;

import static com.google.firebase.auth.Tenant.UpdateRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import java.io.IOException;
import org.junit.Test;

public class TenantTest {

  private static final JsonFactory jsonFactory = Utils.getDefaultJsonFactory();

  @Test
  public void testJsonSerialization() throws IOException {
    Tenant tenant = jsonFactory.fromString(
        "{"
          + "\"tenantId\":\"TENANT_ID\","
          + "\"displayName\":\"DISPLAY_NAME\","
          + "\"allowPasswordSignup\":true,"
          + "\"enableEmailLinkSignin\":false"
          + "}", Tenant.class);

    assertEquals(tenant.getTenantId(), "TENANT_ID");
    assertEquals(tenant.getDisplayName(), "DISPLAY_NAME");
    assertTrue(tenant.isPasswordSignInAllowed());
    assertFalse(tenant.isEmailLinkSignInEnabled());
  }

  @Test
  public void testUpdateRequest() throws IOException {
    Tenant tenant = jsonFactory.fromString(
        "{"
          + "\"tenantId\":\"TENANT_ID\","
          + "\"displayName\":\"DISPLAY_NAME\","
          + "\"allowPasswordSignup\":true,"
          + "\"enableEmailLinkSignin\":false"
          + "}", Tenant.class);

    Tenant.UpdateRequest updateRequest = tenant.updateRequest();

    assertEquals(updateRequest.getTenantId(), "TENANT_ID");
    assertEquals(updateRequest.getDisplayName(), "DISPLAY_NAME");
    assertTrue(updateRequest.isPasswordSignInAllowed());
    assertFalse(updateRequest.isEmailLinkSignInEnabled());
  }
}

