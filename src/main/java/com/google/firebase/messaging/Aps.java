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
import com.google.common.primitives.Booleans;

/**
 * Represents the <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/PayloadKeyReference.html">
 * aps dictionary</a> that is part of every APNS message.
 */
public class Aps {

  @Key("alert")
  private final Object alert;

  @Key("badge")
  private final Integer badge;

  @Key("sound")
  private final String sound;

  @Key("content-available")
  private final Integer contentAvailable;

  @Key("category")
  private final String category;

  @Key("thread-id")
  private final String threadId;

  private Aps(Builder builder) {
    int alerts = Booleans.countTrue(
        !Strings.isNullOrEmpty(builder.alertString),
        builder.alert != null);
    checkArgument(alerts != 2, "Multiple alert specifications (string and ApsAlert) found.");
    if (builder.alert != null) {
      this.alert = builder.alert;
    } else {
      this.alert = builder.alertString;
    }
    this.badge = builder.badge;
    this.sound = builder.sound;
    this.contentAvailable = builder.contentAvailable ? 1 : null;
    this.category = builder.category;
    this.threadId = builder.threadId;
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
    private String category;
    private String threadId;

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
     * Sets the badge to be displayed with the message. Set to 0 to remove the badge. Do not
     * specify any value to keep the badge unchanged.
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
     * Sets whether to configure a background update notification.
     *
     * @param contentAvailable true to perform a background update.
     * @return This builder.
     */
    public Builder setContentAvailable(boolean contentAvailable) {
      this.contentAvailable = contentAvailable;
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

    public Aps build() {
      return new Aps(this);
    }
  }
}
