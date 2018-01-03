/*
 * Copyright 2017 Google Inc.
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
import java.util.ArrayList;
import java.util.List;

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

    private Builder() {
    }

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

    public Builder setColor(String color) {
      this.color = color;
      return this;
    }

    public Builder setSound(String sound) {
      this.sound = sound;
      return this;
    }

    public Builder setTag(String tag) {
      this.tag = tag;
      return this;
    }

    public Builder setClickAction(String clickAction) {
      this.clickAction = clickAction;
      return this;
    }

    public Builder setBodyLocKey(String bodyLocKey) {
      this.bodyLocKey = bodyLocKey;
      return this;
    }

    public Builder addBodyLocArg(String arg) {
      this.bodyLocArgs.add(arg);
      return this;
    }

    public Builder addAllBodyLocArgs(List<String> args) {
      this.bodyLocArgs.addAll(args);
      return this;
    }

    public Builder setTitleLocKey(String titleLocKey) {
      this.titleLocKey = titleLocKey;
      return this;
    }

    public Builder addTitleLocArg(String arg) {
      this.titleLocArgs.add(arg);
      return this;
    }

    public Builder addAllTitleLocArgs(List<String> args) {
      this.titleLocArgs.addAll(args);
      return this;
    }

    public AndroidNotification build() {
      return new AndroidNotification(this);
    }

  }

}
