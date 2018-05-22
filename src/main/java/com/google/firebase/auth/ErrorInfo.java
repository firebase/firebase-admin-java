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

package com.google.firebase.auth;

import java.util.List;

/**
 * Represents an error encountered while importing an {@link ImportUserRecord}.
 */
public final class ErrorInfo {

  private final int index;
  private final String reason;

  ErrorInfo(int index, String reason) {
    this.index = index;
    this.reason = reason;
  }

  /**
   * The index of the failed user in the list passed to the
   * {@link FirebaseAuth#importUsersAsync(List, UserImportOptions)} method.
   *
   * @return an integer index.
   */
  public int getIndex() {
    return index;
  }

  /**
   * A string describing the error.
   *
   * @return A string error message.
   */
  public String getReason() {
    return reason;
  }
}
