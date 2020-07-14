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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.SamlProviderConfig;
import java.util.List;

/**
 * JSON data binding for ListInboundSamlConfigsResponse messages sent by Google identity toolkit
 * service.
 */
public final class ListSamlProviderConfigsResponse
    implements ListProviderConfigsResponse<SamlProviderConfig> {

  @Key("inboundSamlConfigs")
  private List<SamlProviderConfig> providerConfigs;

  @Key("nextPageToken")
  private String pageToken;

  @VisibleForTesting
  public ListSamlProviderConfigsResponse(
      List<SamlProviderConfig> providerConfigs, String pageToken) {
    this.providerConfigs = providerConfigs;
    this.pageToken = pageToken;
  }

  public ListSamlProviderConfigsResponse() { }

  @Override
  public List<SamlProviderConfig> getProviderConfigs() {
    return providerConfigs == null ? ImmutableList.<SamlProviderConfig>of() : providerConfigs;
  }

  @Override
  public boolean hasProviderConfigs() {
    return providerConfigs != null && !providerConfigs.isEmpty();
  }

  @Override
  public String getPageToken() {
    return Strings.nullToEmpty(pageToken);
  }
}
