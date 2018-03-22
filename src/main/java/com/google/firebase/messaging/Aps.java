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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/PayloadKeyReference.html">
 * aps dictionary</a> that is part of every APNS message.
 */
public class Aps {

  private final Map<String, Object> fields;

  private Aps(Builder builder) {
    checkArgument(Strings.isNullOrEmpty(builder.alertString) || (builder.alert == null),
        "Multiple alert specifications (string and ApsAlert) found.");
    ImmutableMap.Builder<String, Object> fields = ImmutableMap.builder();
    if (builder.alert != null) {
      fields.put("alert", builder.alert);
    } else if (builder.alertString != null) {
      fields.put("alert", builder.alertString);
    }
    if (builder.badge != null) {
      fields.put("badge", builder.badge);
    }
    if (builder.sound != null) {
      fields.put("sound", builder.sound);
    }
    if (builder.contentAvailable) {
      fields.put("content-available", 1);
    }
    if (builder.contentMutable) {
      fields.put("content-mutable", 1);
    }
    if (builder.category != null) {
      fields.put("category", builder.category);
    }
    if (builder.threadId != null) {
      fields.put("thread-id", builder.threadId);
    }
    fields.putAll(builder.customFields);
    this.fields = fields.build();
  }

  Map<String, Object> getFields() {
    return this.fields;
  }

  /**
   * Creates a new {@link Aps.Builder}.
   *
   * @return A {@link Aps.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String alertString;
    private ApsAlert alert;
    private Integer badge;
    private String sound;
    private boolean contentAvailable;
    private boolean contentMutable;
    private String category;
    private String threadId;
    private final Map<String, Object> customFields = new HashMap<>();

    private Builder() {}

    /**
     * Sets the alert field as a string.
     *
     * @param alert A string alert.
     * @return This builder.
     */
    public Builder setAlert(String alert) {
      this.alertString = alert;
      return this;
    }

    /**
     * Sets the alert as a dictionary.
     *
     * @param alert An instance of {@link ApsAlert}.
     * @return This builder.
     */
    public Builder setAlert(ApsAlert alert) {
      this.alert = alert;
      return this;
    }

    /**
     * Sets the badge to be displayed with the message. Set to 0 to remove the badge. When not
     * invoked, the badge will remain unchanged.
     *
     * @param badge An integer representing the badge.
     * @return This builder.
     */
    public Builder setBadge(int badge) {
      this.badge = badge;
      return this;
    }

    /**
     * Sets the sound to be played with the message.
     *
     * @param sound Sound file name or {@code "default"}.
     * @return This builder.
     */
    public Builder setSound(String sound) {
      this.sound = sound;
      return this;
    }

    /**
     * Specifies whether to configure a background update notification.
     *
     * @param contentAvailable True to perform a background update.
     * @return This builder.
     */
    public Builder setContentAvailable(boolean contentAvailable) {
      this.contentAvailable = contentAvailable;
      return this;
    }

    /**
     * Specifies whether to set the {@code content-mutable} property on the message, so the
     * clients can modify the notification via app extensions.
     *
     * @param contentMutable True to make the content mutable via app extensions.
     * @return This builder.
     */
    public Builder setContentMutable(boolean contentMutable) {
      this.contentMutable = contentMutable;
      return this;
    }

    /**
     * Puts a custom key-value pair to the aps dictionary.
     *
     * @param key A non-null, non-empty key
     * @param value A non-null, json-serializable value
     * @return This builder
     */
    public Builder putCustomField(String key, @NonNull Object value) {
      this.customFields.put(key, value);
      return this;
    }

    /**
     * Puts all the key-value pairs in the specified map to the aps dictionary.
     *
     * @param fields A map of key-value pairs
     * @return This builder
     */
    public Builder putAllCustomFields(Map<String, Object> fields) {
      this.customFields.putAll(fields);
      return this;
    }

    /**
     * Sets the notification type.
     *
     * @param category A string identifier.
     * @return This builder.
     */
    public Builder setCategory(String category) {
      this.category = category;
      return this;
    }

    /**
     * Sets an app-specific identifier for grouping notifications.
     *
     * @param threadId A string identifier.
     * @return This builder.
     */
    public Builder setThreadId(String threadId) {
      this.threadId = threadId;
      return this;
    }

    /**
     * Builds a new {@link Aps} instance from the fields set on this builder.
     *
     * @return A non-null {@link Aps}.
     * @throws IllegalArgumentException If the alert is specified both as an object and a string.
     *     Or if the same field is set both using a setter method, and as a custom field.
     */
    public Aps build() {
      return new Aps(this);
    }
  }
}
