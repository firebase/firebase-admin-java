/*
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
 * Represents the Android-specific FCM options that can be included in an {@link AndroidConfig}.
 * Instances of this class are thread-safe and immutable.
 */
public final class AndroidFcmOptions {

  @Key("analytics_label")
  private final String analyticsLabel;

  private AndroidFcmOptions(Builder builder) {
    FcmOptionsUtil.checkAnalyticsLabel(builder.analyticsLabel);
    this.analyticsLabel = builder.analyticsLabel;
  }

  /**
   * Creates a new {@link AndroidFcmOptions} object with the specified analytics label.
   *
   * @param analyticsLabel An analytics label
   * @return An {@link AndroidFcmOptions} object with the analytics label set to the supplied value.
   */
  public static AndroidFcmOptions withAnalyticsLabel(String analyticsLabel) {
    return builder().setAnalyticsLabel(analyticsLabel).build();
  }

  /**
   * Creates a new {@link AndroidFcmOptions.Builder}.
   *
   * @return A {@link AndroidFcmOptions.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String analyticsLabel;

    private Builder() {}

    /**
     * @param analyticsLabel A string representing the analytics label used for Android messages.
     * @return This builder
     */
    public Builder setAnalyticsLabel(String analyticsLabel) {
      this.analyticsLabel = analyticsLabel;
      return this;
    }

    /**
     * Creates a new {@link AndroidFcmOptions} instance from the parameters set on this builder.
     *
     * @return A new {@link AndroidFcmOptions} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public AndroidFcmOptions build() {
      return new AndroidFcmOptions(this);
    }
  }
}
