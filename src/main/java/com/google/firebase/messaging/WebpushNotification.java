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
import com.google.firebase.internal.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Webpush-specific notification options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable. Supports most standard options defined
 * in the <a href="https://developer.mozilla.org/en-US/docs/Web/API/notification/Notification">Web
 * Notification specification</a>.
 */
public class WebpushNotification {

  @Key("actions")
  private final List<Action> actions;

  @Key("badge")
  private final String badge;

  @Key("body")
  private final String body;

  @Key("dir")
  private final String direction;

  @Key("icon")
  private final String icon;

  @Key("image")
  private final String image;

  @Key("lang")
  private final String language;

  @Key("renotify")
  private final Boolean renotify;

  @Key("requireInteraction")
  private final Boolean requireInteraction;

  @Key("silent")
  private final Boolean silent;

  @Key("tag")
  private final String tag;

  @Key("timestamp")
  private final Long timestamp;

  @Key("title")
  private final String title;

  @Key("vibrate")
  private final List<Integer> vibrate;

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
    this.actions = !builder.actions.isEmpty() ? ImmutableList.copyOf(builder.actions) : null;
    this.badge = builder.badge;
    this.body = builder.body;
    this.direction = builder.direction != null ? builder.direction.value : null;
    this.icon = builder.icon;
    this.image = builder.image;
    this.language = builder.language;
    this.renotify = builder.renotify;
    this.requireInteraction = builder.requireInteraction;
    this.silent = builder.silent;
    this.tag = builder.tag;
    this.timestamp = builder.timestamp;
    this.title = builder.title;
    this.vibrate = builder.vibrate;
  }

  public static Builder builder() {
    return new Builder();
  }

  public enum Direction {
    AUTO("auto"),
    LEFT_TO_RIGHT("ltr"),
    RIGHT_TO_LEFT("rtl");

    final String value;

    Direction(String value) {
      this.value = value;
    }
  }

  public static class Action {
    @Key("action")
    private final String action;

    @Key("title")
    private final String title;

    @Key("icon")
    private final String icon;

    public Action(String action, String title) {
      this(action, title, null);
    }

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
    private Direction direction;
    private String icon;
    private String image;
    private String language;
    private Boolean renotify;
    private Boolean requireInteraction;
    private Boolean silent;
    private String tag;
    private Long timestamp;
    private String title;
    private List<Integer> vibrate;

    private Builder() {}

    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    public Builder setIcon(String icon) {
      this.icon = icon;
      return this;
    }

    public Builder setBadge(String badge) {
      this.badge = badge;
      return this;
    }

    public Builder setImage(String image) {
      this.image = image;
      return this;
    }

    public Builder setLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder setRenotify(boolean renotify) {
      this.renotify = renotify;
      return this;
    }

    public Builder setRequireInteraction(boolean requireInteraction) {
      this.requireInteraction = requireInteraction;
      return this;
    }

    public Builder setSilent(boolean silent) {
      this.silent = silent;
      return this;
    }

    public Builder setTag(String tag) {
      this.tag = tag;
      return this;
    }

    public Builder setTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder setVibrate(int[] pattern) {
      List<Integer> list = new ArrayList<>();
      for (int value : pattern) {
        list.add(value);
      }
      this.vibrate = ImmutableList.copyOf(list);
      return this;
    }

    public Builder setDirection(Direction direction) {
      this.direction = direction;
      return this;
    }

    public Builder addAction(@NonNull Action action) {
      this.actions.add(action);
      return this;
    }

    public Builder addAllActions(@NonNull List<Action> actions) {
      this.actions.addAll(actions);
      return this;
    }

    public WebpushNotification build() {
      return new WebpushNotification(this);
    }
  }
}
