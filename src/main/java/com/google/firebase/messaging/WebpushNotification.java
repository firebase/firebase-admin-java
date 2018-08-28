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
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the Webpush-specific notification options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable. Supports most standard options defined
 * in the <a href="https://developer.mozilla.org/en-US/docs/Web/API/notification/Notification">Web
 * Notification specification</a>.
 */
public class WebpushNotification {

  private final Map<String, Object> fields;

  /**
   * Creates a new notification with the given title and body. Overrides the options set via
   * {@link Notification}.
   *
   * @param title Title of the notification.
   * @param body Body of the notification.
   */
  public WebpushNotification(String title, String body) {
    this(title, body, null);
  }

  /**
   * Creates a new notification with the given title, body and icon. Overrides the options set via
   * {@link Notification}.
   *
   * @param title Title of the notification.
   * @param body Body of the notification.
   * @param icon URL to the notifications icon.
   */
  public WebpushNotification(String title, String body, @Nullable String icon) {
    this(builder().setTitle(title).setBody(body).setIcon(icon));
  }

  private WebpushNotification(Builder builder) {
    ImmutableMap.Builder<String, Object> fields = ImmutableMap.builder();
    if (!builder.actions.isEmpty()) {
      fields.put("actions", ImmutableList.copyOf(builder.actions));
    }
    addNonNullNonEmpty(fields, "badge", builder.badge);
    addNonNullNonEmpty(fields, "body", builder.body);
    addNonNull(fields, "data", builder.data);
    addNonNullNonEmpty(fields, "dir", builder.direction != null ? builder.direction.value : null);
    addNonNullNonEmpty(fields, "icon", builder.icon);
    addNonNullNonEmpty(fields, "image", builder.image);
    addNonNullNonEmpty(fields, "lang", builder.language);
    addNonNull(fields, "renotify", builder.renotify);
    addNonNull(fields, "requireInteraction", builder.requireInteraction);
    addNonNull(fields, "silent", builder.silent);
    addNonNullNonEmpty(fields, "tag", builder.tag);
    addNonNull(fields, "timestamp", builder.timestampMillis);
    addNonNullNonEmpty(fields, "title", builder.title);
    addNonNull(fields, "vibrate", builder.vibrate);
    fields.putAll(builder.customData);
    this.fields = fields.build();
  }

  Map<String, Object> getFields() {
    return fields;
  }

  /**
   * Creates a new {@link WebpushNotification.Builder}.
   *
   * @return A {@link WebpushNotification.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Different directions a notification can be displayed in.
   */
  public enum Direction {
    AUTO("auto"),
    LEFT_TO_RIGHT("ltr"),
    RIGHT_TO_LEFT("rtl");

    final String value;

    Direction(String value) {
      this.value = value;
    }
  }

  /**
   * Represents an action available to users when the notification is presented.
   */
  public static class Action {
    @Key("action")
    private final String action;

    @Key("title")
    private final String title;

    @Key("icon")
    private final String icon;

    /**
     * Creates a new Action with the given action string and title.
     *
     * @param action Action string.
     * @param title Title text.
     */
    public Action(String action, String title) {
      this(action, title, null);
    }

    /**
     * Creates a new Action with the given action string, title and icon URL.
     *
     * @param action Action string.
     * @param title Title text.
     * @param icon Icon URL or null.
     */
    public Action(String action, String title, @Nullable String icon) {
      checkArgument(!Strings.isNullOrEmpty(action));
      checkArgument(!Strings.isNullOrEmpty(title));
      this.action = action;
      this.title = title;
      this.icon = icon;
    }
  }

  public static class Builder {

    private final List<Action> actions = new ArrayList<>();
    private String badge;
    private String body;
    private Object data;
    private Direction direction;
    private String icon;
    private String image;
    private String language;
    private Boolean renotify;
    private Boolean requireInteraction;
    private Boolean silent;
    private String tag;
    private Long timestampMillis;
    private String title;
    private List<Integer> vibrate;
    private final Map<String, Object> customData = new HashMap<>();

    private Builder() {}

    /**
     * Adds a notification action to the notification.
     *
     * @param action A non-null {@link Action}.
     * @return This builder.
     */
    public Builder addAction(@NonNull Action action) {
      this.actions.add(action);
      return this;
    }

    /**
     * Adds all the actions in the given list to the notification.
     *
     * @param actions A non-null list of actions.
     * @return This builder.
     */
    public Builder addAllActions(@NonNull List<Action> actions) {
      this.actions.addAll(actions);
      return this;
    }

    /**
     * Sets the URL of the image used to represent the notification when there is
     * not enough space to display the notification itself.
     *
     * @param badge Badge URL.
     * @return This builder.
     */
    public Builder setBadge(String badge) {
      this.badge = badge;
      return this;
    }

    /**
     * Sets the body text of the notification.
     *
     * @param body Body text.
     * @return This builder.
     */
    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * Sets any arbitrary data that should be associated with the notification.
     *
     * @param data A JSON-serializable object.
     * @return This builder.
     */
    public Builder setData(Object data) {
      this.data = data;
      return this;
    }

    /**
     * Sets the direction in which to display the notification.
     *
     * @param direction Direction enum value.
     * @return This builder.
     */
    public Builder setDirection(Direction direction) {
      this.direction = direction;
      return this;
    }

    /**
     * Sets the URL to the icon of the notification.
     *
     * @param icon Icon URL.
     * @return This builder.
     */
    public Builder setIcon(String icon) {
      this.icon = icon;
      return this;
    }

    /**
     * Sets the URL of an image to be displayed in the notification.
     *
     * @param image Image URL
     * @return This builder.
     */
    public Builder setImage(String image) {
      this.image = image;
      return this;
    }

    /**
     * Sets the language of the notification.
     *
     * @param language Notification language.
     * @return This builder.
     */
    public Builder setLanguage(String language) {
      this.language = language;
      return this;
    }

    /**
     * Sets whether the user should be notified after a new notification replaces an old one.
     *
     * @param renotify true to notify the user on replacement.
     * @return This builder.
     */
    public Builder setRenotify(boolean renotify) {
      this.renotify = renotify;
      return this;
    }

    /**
     * Sets whether a notification should remain active until the user clicks or dismisses it,
     * rather than closing automatically.
     *
     * @param requireInteraction true to keep the notification active until user interaction.
     * @return This builder.
     */
    public Builder setRequireInteraction(boolean requireInteraction) {
      this.requireInteraction = requireInteraction;
      return this;
    }

    /**
     * Sets whether the notification should be silent.
     *
     * @param silent true to indicate that the notification should be silent.
     * @return This builder.
     */
    public Builder setSilent(boolean silent) {
      this.silent = silent;
      return this;
    }

    /**
     * Sets an identifying tag on the notification.
     *
     * @param tag A tag to be associated with the notification.
     * @return This builder.
     */
    public Builder setTag(String tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets a timestamp value in milliseconds on the notification.
     *
     * @param timestampMillis A timestamp value as a number.
     * @return This builder.
     */
    public Builder setTimestampMillis(long timestampMillis) {
      this.timestampMillis = timestampMillis;
      return this;
    }

    /**
     * Sets the title text of the notification.
     *
     * @param title Title text.
     * @return This builder.
     */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /**
     * Sets a vibration pattern for the device's vibration hardware to emit
     * when the notification fires.
     *
     * @param pattern An integer array representing a vibration pattern.
     * @return This builder.
     */
    public Builder setVibrate(int[] pattern) {
      List<Integer> list = new ArrayList<>();
      for (int value : pattern) {
        list.add(value);
      }
      this.vibrate = ImmutableList.copyOf(list);
      return this;
    }

    /**
     * Puts a custom key-value pair to the notification.
     *
     * @param key A non-null key.
     * @param value A non-null, json-serializable value.
     * @return This builder.
     */
    public Builder putCustomData(@NonNull String key, @NonNull Object value) {
      this.customData.put(key, value);
      return this;
    }

    /**
     * Puts all the key-value pairs in the specified map to the notification.
     *
     * @param fields A non-null map. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllCustomData(@NonNull Map<String, Object> fields) {
      this.customData.putAll(fields);
      return this;
    }

    /**
     * Creates a new {@link WebpushNotification} from the parameters set on this builder.
     *
     * @return A new {@link WebpushNotification} instance.
     */
    public WebpushNotification build() {
      return new WebpushNotification(this);
    }
  }

  private static void addNonNull(
      ImmutableMap.Builder<String, Object> fields, String key, Object value) {
    if (value != null) {
      fields.put(key, value);
    }
  }

  private static void addNonNullNonEmpty(
      ImmutableMap.Builder<String, Object> fields, String key, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      fields.put(key, value);
    }
  }
}
