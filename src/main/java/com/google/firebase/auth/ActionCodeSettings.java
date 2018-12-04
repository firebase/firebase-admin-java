/*
 * Copyright 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import com.google.firebase.internal.NonNull;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Defines the required continue/state URL with optional Android and iOS settings. Used when
 * invoking the email action link generation APIs in {@link FirebaseAuth}.
 */
public final class ActionCodeSettings {

  private final Map<String, Object> properties;

  private ActionCodeSettings(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.url), "URL must not be null or empty");
    try {
      new URL(builder.url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed URL string", e);
    }
    ImmutableMap.Builder<String, Object> properties = ImmutableMap.<String, Object>builder()
            .put("continueUrl", builder.url)
            .put("canHandleCodeInApp", builder.handleCodeInApp);
    if (!Strings.isNullOrEmpty(builder.dynamicLinkDomain)) {
      properties.put("dynamicLinkDomain", builder.dynamicLinkDomain);
    }
    if (builder.androidActionCodeSettings != null) {
      properties.putAll(builder.androidActionCodeSettings.getProperties());
    }
    if (builder.iosActionCodeSettings != null) {
      properties.putAll(builder.iosActionCodeSettings.getProperties());
    }
    this.properties = properties.build();
  }

  Map<String, Object> getProperties() {
    return this.properties;
  }

  /**
   * Creates a new {@link ActionCodeSettings.Builder}.
   *
   * @return A {@link ActionCodeSettings.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String url;
    private boolean handleCodeInApp;
    private String dynamicLinkDomain;
    private AndroidActionCodeSettings androidActionCodeSettings;
    private IosActionCodeSettings iosActionCodeSettings;

    private Builder() { }

    /**
     * Sets the link continue/state URL, which has different meanings in different contexts:
     *
     * <ul>
     * <li>When the link is handled in the web action widgets, this is the deep link in the
     * {@code continueUrl} query parameter.</li>
     * <li>When the link is handled in the app directly, this is the {@code continueUrl} query
     * parameter in the deep link of the Dynamic Link.</li>
     * </ul>
     *
     * <p>This parameter must be specified when creating a new {@link ActionCodeSettings} instance.
     *
     * @param url Continue/state URL string.
     * @return This builder.
     */
    public Builder setUrl(@NonNull String url) {
      this.url = url;
      return this;
    }

    /**
     * Specifies whether to open the link via a mobile app or a browser. The default is false.
     * When set to true, the action code link is sent as a Universal Link or an Android App Link
     * and is opened by the app if installed. In the false case, the code is sent to the web widget
     * first and then redirects to the app if installed.
     *
     * @param handleCodeInApp true to open the link in the app, and false otherwise.
     * @return This builder.
     */
    public Builder setHandleCodeInApp(boolean handleCodeInApp) {
      this.handleCodeInApp = handleCodeInApp;
      return this;
    }

    /**
     * Sets the dynamic link domain to use for the current link if it is to be opened using
     * Firebase Dynamic Links, as multiple dynamic link domains can be configured per project. This
     * setting provides the ability to explicitly choose one. If none is provided, the oldest
     * domain is used by default.
     *
     * @param dynamicLinkDomain Firebase Dynamic Link domain string.
     * @return This builder.
     */
    public Builder setDynamicLinkDomain(String dynamicLinkDomain) {
      this.dynamicLinkDomain = dynamicLinkDomain;
      return this;
    }

    /**
     * Sets the Android package name and other platform-specific settings. This attempts to open
     * the link in an Android app if it is installed.
     *
     * @param androidActionCodeSettings Android-specific settings for how the link should be
     *     handled.
     * @return This builder.
     */
    public Builder setAndroidActionCodeSettings(
            AndroidActionCodeSettings androidActionCodeSettings) {
      this.androidActionCodeSettings = androidActionCodeSettings;
      return this;
    }

    /**
     * Sets the iOS bundle ID. This will try to open the link in an iOS app if it is installed.
     *
     * @param iosActionCodeSettings iOS-specific settings for how the link should be handled.
     * @return This builder.
     */
    public Builder setIosActionCodeSettings(IosActionCodeSettings iosActionCodeSettings) {
      this.iosActionCodeSettings = iosActionCodeSettings;
      return this;
    }

    /**
     * Builds a new {@link ActionCodeSettings}.
     *
     * @return A non-null {@link ActionCodeSettings}.
     */
    public ActionCodeSettings build() {
      return new ActionCodeSettings(this);
    }
  }
}
