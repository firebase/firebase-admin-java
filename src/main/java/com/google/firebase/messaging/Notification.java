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

import com.google.api.client.util.Key;

/**
 * Represents the notification parameters that can be included in a {@link Message}. Instances
 * of this class are thread-safe and immutable.
 */
public class Notification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;
  
  @Key("image")
  private final String image;

  private Notification(Builder builder) {
    this.title = builder.title;
    this.body = builder.body;
    this.image = builder.image;
  }

  /**
   * Creates a new {@link Builder}.
   *
   * @return A {@link Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String title;
    private String body;
    private String image;

    private Builder() {}

    /**
     * Sets the title of the notification.
     *
     * @param title Title of the notification.
     * @return This builder.
     */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /**
     * Sets the body of the notification.
     *
     * @param body Body of the notification.
     * @return This builder.
     */
    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * Sets the URL of the image that is going to be displayed in the notification.
     *
     * @param imageUrl URL of the image that is going to be displayed in the notification.
     * @return This builder.
     */
    public Builder setImage(String imageUrl) {
      this.image = imageUrl;
      return this;
    }

    /**
     * Creates a new {@link Notification} instance from the parameters set on this builder.
     *
     * @return A new {@link Notification} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public Notification build() {
      return new Notification(this);
    }
  }

}
