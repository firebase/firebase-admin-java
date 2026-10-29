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

import static com.google.common.base.Preconditions.checkNotNull;

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
     * To use the replay protection feature, set this to {@code true}. The
     * {@link FirebaseAppCheck#verifyToken} method will mark the token as consumed after
     * verifying it.
     *
     * <p>Tokens that are found to be already consumed will not throw an exception. Instead,
     * {@link VerifyAppCheckTokenResponse#isAlreadyConsumed()} returns {@code Optional.of(true)}
     * in the response.
     *
     * <p>Tokens are only considered to be consumed if they are sent to the App Check backend by
     * calling {@link FirebaseAppCheck#verifyToken} with this option set to {@code true}; other
     * uses of the token do not consume it.
     *
     * <p>This replay protection feature requires an additional network call to the App Check
     * backend and forces your clients to obtain a fresh attestation from your chosen attestation
     * providers. This can therefore negatively impact performance and can potentially deplete
     * your attestation providers' quotas faster. We recommend that you use this feature only for
     * protecting low-volume, security-critical, or expensive operations.
     *
     * @param consume Set to {@code true} to consume the token.
     * @return This builder.
     */
    public Builder setConsume(boolean consume) {
      this.consume = Optional.of(consume);
      return this;
    }

    /**
     * Sets whether to consume the token during verification. See
     * {@link #setConsume(boolean)} for details about the replay protection feature.
     *
     * @param consume Optional boolean value. Must not be null.
     * @return This builder.
     */
    public Builder setConsume(Optional<Boolean> consume) {
      this.consume = checkNotNull(consume, "consume must not be null");
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
