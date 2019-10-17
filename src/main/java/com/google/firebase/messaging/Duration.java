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
 * A class representing duration in an LightSettings.
 */
public class Duration {
  
  @Key("seconds")
  private final Long seconds;
  
  @Key("nanos")
  private final Integer nanos;

  private Duration(Builder builder) {
    this.seconds = builder.seconds;
    this.nanos = builder.nanos;
  }
  
  /**
   * Creates a new {@link Duration.Builder}.
   *
   * @return A {@link Duration.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }
  
  public static class Builder {

    private Long seconds;
    private Integer nanos;

    private Builder() {}

    /**
     * Sets the duration in milliseconds.   
     *
     * @param durationInMillis The time duration in milliseconds.
     * @return This builder.
     */
    public Builder fromLongInMillis(Long durationInMillis) {
      this.seconds = durationInMillis / 1000;
      this.nanos = (int) (durationInMillis % 1000) * 1000;
      return this;
    }

    public Duration build() {
      return new Duration(this);
    }
  }
}
