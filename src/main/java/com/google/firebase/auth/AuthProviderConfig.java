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
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The base class for Auth providers.
 */
public abstract class AuthProviderConfig {

  @Key("name")
  private String providerId;

  @Key("displayName")
  private String displayName;

  @Key("enabled")
  private boolean enabled;

  public String getProviderId() {
    return providerId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * A base specification class for creating a new provider.
   *
   * <p>Set the initial attributes of the new provider by calling various setter methods available
   * in this class.
   */
  public abstract static class CreateRequest {

    final Map<String,Object> properties = new HashMap<>();

    /**
     * Sets the ID for the new provider.
     *
     * @param providerId a non-null, non-empty provider ID string.
     */
    public CreateRequest setProviderId(String providerId) {
      checkArgument(
          !Strings.isNullOrEmpty(providerId), "provider ID name must not be null or empty");
      properties.put("name", providerId);
      return this;
    }

    /**
     * Sets the display name for the new provider.
     *
     * @param displayName a non-null, non-empty display name string.
     */
    public CreateRequest setDisplayName(String displayName) {
      checkArgument(!Strings.isNullOrEmpty(displayName), "display name must not be null or empty");
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Sets whether to allow the user to sign in with the provider.
     *
     * @param enabled a boolean indicating whether the user can sign in with the provider
     */
    public CreateRequest setEnabled(boolean enabled) {
      properties.put("enabled", enabled);
      return this;
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }
  }
}
