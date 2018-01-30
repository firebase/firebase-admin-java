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
 * Represents the Android-specific notification options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable.
 */
public class AndroidNotification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;

  @Key("icon")
  private final String icon;

  @Key("color")
  private final String color;

  @Key("sound")
  private final String sound;

  @Key("tag")
  private final String tag;

  @Key("click_action")
  private final String clickAction;

  @Key("body_loc_key")
  private final String bodyLocKey;

  @Key("body_loc_args")
  private final List<String> bodyLocArgs;

  @Key("title_loc_key")
  private final String titleLocKey;

  @Key("title_loc_args")
  private final List<String> titleLocArgs;

  private AndroidNotification(Builder builder) {
    this.title = builder.title;
    this.body = builder.body;
    this.icon = builder.icon;
    if (builder.color != null) {
      checkArgument(builder.color.matches("^#[0-9a-fA-F]{6}$"),
          "color must be in the form #RRGGBB");
    }
    this.color = builder.color;
    this.sound = builder.sound;
    this.tag = builder.tag;
    this.clickAction = builder.clickAction;
    this.bodyLocKey = builder.bodyLocKey;
    if (!builder.bodyLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.bodyLocKey),
          "bodyLocKey is required when specifying bodyLocArgs");
      this.bodyLocArgs = ImmutableList.copyOf(builder.bodyLocArgs);
    } else {
      this.bodyLocArgs = null;
    }

    this.titleLocKey = builder.titleLocKey;
    if (!builder.titleLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.titleLocKey),
          "titleLocKey is required when specifying titleLocArgs");
      this.titleLocArgs = ImmutableList.copyOf(builder.titleLocArgs);
    } else {
      this.titleLocArgs = null;
    }
  }

  /**
   * Creates a new {@link AndroidNotification.Builder}.
   *
   * @return A {@link AndroidNotification.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String title;
    private String body;
    private String icon;
    private String color;
    private String sound;
    private String tag;
    private String clickAction;
    private String bodyLocKey;
    private List<String> bodyLocArgs = new ArrayList<>();
    private String titleLocKey;
    private List<String> titleLocArgs = new ArrayList<>();

    private Builder() {}

    /**
     * Sets the title of the Android notification. When provided, overrides the title set
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
     * Sets the body of the Android notification. When provided, overrides the body sent
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
     * Sets the icon of the Android notification.
     *
     * @param icon Icon resource for the notification.
     * @return This builder.
     */
    public Builder setIcon(String icon) {
      this.icon = icon;
      return this;
    }

    /**
     * Sets the notification icon color.
     *
     * @param color Color specified in the {@code #rrggbb} format.
     * @return This builder.
     */
    public Builder setColor(String color) {
      this.color = color;
      return this;
    }

    /**
     * Sets the sound to be played when the device receives the notification.
     *
     * @param sound File name of the sound resource or "default".
     * @return This builder.
     */
    public Builder setSound(String sound) {
      this.sound = sound;
      return this;
    }

    /**
     * Sets the notification tag. This is an identifier used to replace existing notifications in
     * the notification drawer. If not specified, each request creates a new notification.
     *
     * @param tag Notification tag.
     * @return This builder.
     */
    public Builder setTag(String tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets the action associated with a user click on the notification. If specified, an activity
     * with a matching Intent Filter is launched when a user clicks on the notification.
     *
     * @param clickAction Click action name.
     * @return This builder.
     */
    public Builder setClickAction(String clickAction) {
      this.clickAction = clickAction;
      return this;
    }

    /**
     * Sets the key of the body string in the app's string resources to use to localize the body
     * text.
     *
     * @param bodyLocKey Resource key string.
     * @return This builder.
     */
    public Builder setBodyLocalizationKey(String bodyLocKey) {
      this.bodyLocKey = bodyLocKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addBodyLocalizationArg(@NonNull String arg) {
      this.bodyLocArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllBodyLocalizationArgs(@NonNull List<String> args) {
      this.bodyLocArgs.addAll(args);
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
    public Builder addAllTitleLocalizationArgs(@NonNull List<String> args) {
      this.titleLocArgs.addAll(args);
      return this;
    }

    /**
     * Creates a new {@link AndroidNotification} instance from the parameters set on this builder.
     *
     * @return A new {@link AndroidNotification} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public AndroidNotification build() {
      return new AndroidNotification(this);
    }
  }
}
