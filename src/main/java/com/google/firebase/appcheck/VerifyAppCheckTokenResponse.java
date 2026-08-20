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

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.Optional;

/**
 * Represents the response from verifying a Firebase App Check token.
 */
public final class VerifyAppCheckTokenResponse {

  private final String appId;
  private final DecodedAppCheckToken token;
  private final Optional<Boolean> alreadyConsumed;

  VerifyAppCheckTokenResponse(
      @NonNull String appId,
      @NonNull DecodedAppCheckToken token,
      @Nullable Boolean alreadyConsumed) {
    this.appId = checkNotNull(appId, "appId must not be null");
    this.token = checkNotNull(token, "token must not be null");
    this.alreadyConsumed = Optional.ofNullable(alreadyConsumed);
  }

  /**
   * Returns the App ID associated with the App Check token.
   */
  public String getAppId() {
    return appId;
  }

  /**
   * Returns the decoded App Check token.
   */
  public DecodedAppCheckToken getToken() {
    return token;
  }

  /**
   * Returns whether the token was already consumed prior to verification, if consume option was requested.
   */
  public Optional<Boolean> isAlreadyConsumed() {
    return alreadyConsumed;
  }
}
