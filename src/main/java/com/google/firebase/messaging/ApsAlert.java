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

package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/PayloadKeyReference.html#//apple_ref/doc/uid/TP40008194-CH17-SW5">
 * alert property</a> that can be included in the aps dictionary of an APNS payload.
 */
public class ApsAlert {

  @Key("title")
  private final String title;

  @Key("subtitle")
  private final String subtitle;

  @Key("body")
  private final String body;

  @Key("loc-key")
  private final String locKey;

  @Key("loc-args")
  private final List<String> locArgs;

  @Key("title-loc-key")
  private final String titleLocKey;

  @Key("title-loc-args")
  private final List<String> titleLocArgs;

  @Key("subtitle-loc-key")
  private final String subtitleLocKey;

  @Key("subtitle-loc-args")
  private final List<String> subtitleLocArgs;

  @Key("action-loc-key")
  private final String actionLocKey;

  @Key("launch-image")
  private final String launchImage;

  private ApsAlert(Builder builder) {
    this.title = builder.title;
    this.subtitle = builder.subtitle;
    this.body = builder.body;
    this.actionLocKey = builder.actionLocKey;
    this.locKey = builder.locKey;
    if (!builder.locArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.locKey),
          "locKey is required when specifying locArgs");
      this.locArgs = ImmutableList.copyOf(builder.locArgs);
    } else {
      this.locArgs = null;
    }

    this.titleLocKey = builder.titleLocKey;
    if (!builder.titleLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.titleLocKey),
          "titleLocKey is required when specifying titleLocArgs");
      this.titleLocArgs = ImmutableList.copyOf(builder.titleLocArgs);
    } else {
      this.titleLocArgs = null;
    }
    this.subtitleLocKey = builder.subtitleLocKey;
    if (!builder.subtitleLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.subtitleLocKey),
          "subtitleLocKey is required when specifying subtitleLocArgs");
      this.subtitleLocArgs = ImmutableList.copyOf(builder.subtitleLocArgs);
    } else {
      this.subtitleLocArgs = null;
    }
    this.launchImage = builder.launchImage;
  }

  /**
   * Creates a new {@link ApsAlert.Builder}.
   *
   * @return A {@link ApsAlert.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String title;
    private String subtitle;
    private String body;
    private String locKey;
    private List<String> locArgs = new ArrayList<>();
    private String titleLocKey;
    private List<String> titleLocArgs = new ArrayList<>();
    private String subtitleLocKey;
    private List<String> subtitleLocArgs = new ArrayList<>();
    private String actionLocKey;
    private String launchImage;

    private Builder() {}

    /**
     * Sets the title of the alert. When provided, overrides the title sent
     * via {@link Notification}.
     *
     * @param title Title of the notification.
     * @return This builder.
     */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /**
     * Sets the subtitle of the alert.
     *
     * @param subtitle Subtitle of the notification.
     * @return This builder.
     */
    public Builder setSubtitle(String subtitle) {
      this.subtitle = subtitle;
      return this;
    }

    /**
     * Sets the body of the alert. When provided, overrides the body sent
     * via {@link Notification}.
     *
     * @param body Body of the notification.
     * @return This builder.
     */
    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * Sets the key of the text in the app's string resources to use to localize the action button
     * text.
     *
     * @param actionLocKey Resource key string.
     * @return This builder.
     */
    public Builder setActionLocalizationKey(String actionLocKey) {
      this.actionLocKey = actionLocKey;
      return this;
    }

    /**
     * Sets the key of the body string in the app's string resources to use to localize the body
     * text.
     *
     * @param locKey Resource key string.
     * @return This builder.
     */
    public Builder setLocalizationKey(String locKey) {
      this.locKey = locKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addLocalizationArg(@NonNull String arg) {
      this.locArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllLocalizationArgs(@NonNull List<String> args) {
      this.locArgs.addAll(args);
      return this;
    }

    /**
     * Sets the key of the title string in the app's string resources to use to localize the title
     * text.
     *
     * @param titleLocKey Resource key string.
     * @return This builder.
     */
    public Builder setTitleLocalizationKey(String titleLocKey) {
      this.titleLocKey = titleLocKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code titleLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addTitleLocalizationArg(@NonNull String arg) {
      this.titleLocArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code titleLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllTitleLocArgs(@NonNull List<String> args) {
      this.titleLocArgs.addAll(args);
      return this;
    }

    /**
     * Sets the key of the subtitle string in the app's string resources to use to localize 
     * the subtitle text.
     *
     * @param subtitleLocKey Resource key string.
     * @return This builder.
     */
    public Builder setSubtitleLocalizationKey(String subtitleLocKey) {
      this.subtitleLocKey = subtitleLocKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code subtitleLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addSubtitleLocalizationArg(@NonNull String arg) {
      this.subtitleLocArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code subtitleLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllSubtitleLocArgs(@NonNull List<String> args) {
      this.subtitleLocArgs.addAll(args);
      return this;
    }

    /**
     * Sets the launch image for the notification action.
     *
     * @param launchImage An image file name.
     * @return This builder.
     */
    public Builder setLaunchImage(String launchImage) {
      this.launchImage = launchImage;
      return this;
    }

    /**
     * Creates a new {@link ApsAlert} instance from the parameters set on this builder.
     *
     * @return A new {@link ApsAlert} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public ApsAlert build() {
      return new ApsAlert(this);
    }
  }

}
