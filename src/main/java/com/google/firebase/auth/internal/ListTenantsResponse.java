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

import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.multitenancy.Tenant;
import java.util.List;

/**
 * JSON data binding for ListTenantsResponse messages sent by Google identity toolkit service.
 */
public final class ListTenantsResponse {

  @Key("tenants")
  private List<Tenant> tenants;

  @Key("pageToken")
  private String pageToken;

  @VisibleForTesting
  public ListTenantsResponse(List<Tenant> tenants, String pageToken) {
    this.tenants = tenants;
    this.pageToken = pageToken;
  }

  public ListTenantsResponse() { }

  public List<Tenant> getTenants() {
    return tenants == null ? ImmutableList.<Tenant>of() : tenants;
  }

  public boolean hasTenants() {
    return tenants != null && !tenants.isEmpty();
  }

  public String getPageToken() {
    return pageToken == null ? "" : pageToken;
  }
}
