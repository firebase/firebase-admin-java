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
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.multitenancy.Tenant;

import org.junit.Test;

public class ListTenantsResponseTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  @Test
  public void testDefaultValues() throws Exception {
    ListTenantsResponse response = new ListTenantsResponse();

    assertEquals(0, response.getTenants().size());
    assertFalse(response.hasTenants());
    assertEquals("", response.getPageToken());
  }

  @Test
  public void testEmptyTenantList() throws Exception {
    ListTenantsResponse response =
        new ListTenantsResponse(ImmutableList.<Tenant>of(), "PAGE_TOKEN");

    assertEquals(0, response.getTenants().size());
    assertFalse(response.hasTenants());
  }

  @Test
  public void testDeserialization() throws Exception {
    String json = JSON_FACTORY.toString(
        ImmutableMap.of(
          "tenants", ImmutableList.of(
            ImmutableMap.of("name", "projects/project-id/resource/TENANT_1"),
            ImmutableMap.of("name", "projects/project-id/resource/TENANT_2")),
          "pageToken", "PAGE_TOKEN"));
    ListTenantsResponse response = JSON_FACTORY.fromString(json, ListTenantsResponse.class);

    assertEquals(2, response.getTenants().size());
    assertEquals("TENANT_1", response.getTenants().get(0).getTenantId());
    assertEquals("TENANT_2", response.getTenants().get(1).getTenantId());
    assertTrue(response.hasTenants());
    assertEquals("PAGE_TOKEN", response.getPageToken());
  }
}
