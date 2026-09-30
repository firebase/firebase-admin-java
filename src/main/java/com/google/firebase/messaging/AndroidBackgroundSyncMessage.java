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

/**
 * Represents a background sync message in Android v2 config.
 * Instances of this class are thread-safe and immutable.
 */
public final class AndroidBackgroundSyncMessage {

  private AndroidBackgroundSyncMessage(Builder builder) {}

  /**
   * Creates a new {@link AndroidBackgroundSyncMessage.Builder}.
   *
   * @return An {@link AndroidBackgroundSyncMessage.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Builder() {}

    /**
     * Creates a new {@link AndroidBackgroundSyncMessage} instance from the parameters set
     * on this builder.
     *
     * @return A new {@link AndroidBackgroundSyncMessage} instance.
     */
    public AndroidBackgroundSyncMessage build() {
      return new AndroidBackgroundSyncMessage(this);
    }
  }
}
