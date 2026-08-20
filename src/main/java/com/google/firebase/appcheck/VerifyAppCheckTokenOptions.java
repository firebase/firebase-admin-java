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

package com.google.firebase.appcheck;

import java.util.Optional;

/**
 * Options for verifying a Firebase App Check token.
 */
public final class VerifyAppCheckTokenOptions {

  private final Optional<Boolean> consume;

  private VerifyAppCheckTokenOptions(Builder builder) {
    this.consume = builder.consume;
  }

  /**
   * Returns whether to consume the App Check token during verification for replay protection.
   */
  public Optional<Boolean> getConsume() {
    return consume;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private Optional<Boolean> consume = Optional.empty();

    private Builder() {}

    /**
     * Sets whether to consume the token during verification.
     *
     * @param consume Set to true to consume the token.
     * @return This builder.
     */
    public Builder setConsume(boolean consume) {
      this.consume = Optional.of(consume);
      return this;
    }

    /**
     * Sets whether to consume the token during verification.
     *
     * @param consume Optional boolean value.
     * @return This builder.
     */
    public Builder setConsume(Optional<Boolean> consume) {
      this.consume = consume != null ? consume : Optional.<Boolean>empty();
      return this;
    }

    /**
     * Builds a new {@link VerifyAppCheckTokenOptions} instance.
     */
    public VerifyAppCheckTokenOptions build() {
      return new VerifyAppCheckTokenOptions(this);
    }
  }
}
