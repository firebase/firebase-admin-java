/*
 * Copyright 2026 Google LLC
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.Key;
import com.google.firebase.internal.NonNull;

/**
 * Represents a remote notification in Android v2 config.
 * Instances of this class are thread-safe and immutable.
 */
public final class AndroidRemoteNotification {

  @Key("mutable_content")
  private final Boolean mutableContent;

  @Key("notification")
  private final AndroidNotificationV2 notification;

  @Key("use_as_v1_data_message")
  private final Boolean useAsV1DataMessage;

  private AndroidRemoteNotification(Builder builder) {
    this.mutableContent = builder.mutableContent;
    this.notification = checkNotNull(
        builder.notification, "notification must not be null");
    this.useAsV1DataMessage = builder.useAsV1DataMessage;
  }

  /**
   * Creates a new {@link AndroidRemoteNotification.Builder}.
   *
   * @return An {@link AndroidRemoteNotification.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Boolean mutableContent;
    private AndroidNotificationV2 notification;
    private Boolean useAsV1DataMessage;

    private Builder() {}

    /**
     * Sets the mutable content flag.
     *
     * @param mutableContent The mutable content flag.
     * @return This builder.
     */
    public Builder setMutableContent(boolean mutableContent) {
      this.mutableContent = mutableContent;
      return this;
    }

    /**
     * Sets the notification.
     *
     * @param notification Android notification v2 config. Must not be null.
     * @return This builder.
     */
    public Builder setNotification(@NonNull AndroidNotificationV2 notification) {
      this.notification = notification;
      return this;
    }

    /**
     * Sets whether to use this message as a v1 data message on legacy clients.
     *
     * @param useAsV1DataMessage The legacy data message flag.
     * @return This builder.
     */
    public Builder setUseAsV1DataMessage(boolean useAsV1DataMessage) {
      this.useAsV1DataMessage = useAsV1DataMessage;
      return this;
    }

    /**
     * Creates a new {@link AndroidRemoteNotification} instance from the parameters set
     * on this builder.
     *
     * @return A new {@link AndroidRemoteNotification} instance.
     */
    public AndroidRemoteNotification build() {
      return new AndroidRemoteNotification(this);
    }
  }
}
