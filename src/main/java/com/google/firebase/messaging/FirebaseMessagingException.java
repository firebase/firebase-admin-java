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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseException;
import com.google.firebase.internal.NonNull;

public class FirebaseMessagingException extends FirebaseException {

  private final String errorCode;

  FirebaseMessagingException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    checkArgument(!Strings.isNullOrEmpty(errorCode));
    this.errorCode = errorCode;
  }


  /** Returns an error code that may provide more information about the error. */
  @NonNull
  public String getErrorCode() {
    return errorCode;
  }
}
