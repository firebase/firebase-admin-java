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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * The base class for Auth providers.
 */
public abstract class ProviderConfig {

  @Key("name")
  private String resourceName;

  @Key("displayName")
  private String displayName;

  @Key("enabled")
  private boolean enabled;

  public String getProviderId() {
    return resourceName.substring(resourceName.lastIndexOf("/") + 1);
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  static void assertValidUrl(String url) throws IllegalArgumentException {
    try {
      new URL(url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(url + " is a malformed URL.", e);
    }
  }

  /**
   * A base specification class for creating a new provider.
   *
   * <p>Set the initial attributes of the new provider by calling various setter methods available
   * in this class.
   */
  public abstract static class AbstractCreateRequest<T extends AbstractCreateRequest<T>> {

    final Map<String,Object> properties = new HashMap<>();
    String providerId;

    T setProviderId(String providerId) {
      this.providerId = providerId;
      return getThis();
    }

    String getProviderId() {
      return providerId;
    }

    /**
     * Sets the display name for the new provider.
     *
     * @param displayName A non-null, non-empty display name string.
     * @throws IllegalArgumentException If the display name is null or empty.
     */
    public T setDisplayName(String displayName) {
      checkArgument(!Strings.isNullOrEmpty(displayName), "Display name must not be null or empty.");
      properties.put("displayName", displayName);
      return getThis();
    }

    /**
     * Sets whether to allow the user to sign in with the provider.
     *
     * @param enabled A boolean indicating whether the user can sign in with the provider.
     */
    public T setEnabled(boolean enabled) {
      properties.put("enabled", enabled);
      return getThis();
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }

    abstract T getThis();
  }

  /**
   * A base class for updating the attributes of an existing provider.
   */
  public abstract static class AbstractUpdateRequest<T extends AbstractUpdateRequest<T>> {

    final String providerId;
    final Map<String,Object> properties = new HashMap<>();

    AbstractUpdateRequest(String providerId) {
      checkArgument(!Strings.isNullOrEmpty(providerId), "Provider ID must not be null or empty.");
      this.providerId = providerId;
    }

    String getProviderId() {
      return providerId;
    }

    /**
     * Sets the display name for the existing provider.
     *
     * @param displayName A non-null, non-empty display name string.
     * @throws IllegalArgumentException If the display name is null or empty.
     */
    public T setDisplayName(String displayName) {
      checkArgument(!Strings.isNullOrEmpty(displayName), "Display name must not be null or empty.");
      properties.put("displayName", displayName);
      return getThis();
    }

    /**
     * Sets whether to allow the user to sign in with the provider.
     *
     * @param enabled A boolean indicating whether the user can sign in with the provider.
     */
    public T setEnabled(boolean enabled) {
      properties.put("enabled", enabled);
      return getThis();
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }

    abstract T getThis();
  }
}
