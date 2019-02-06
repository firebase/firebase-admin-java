/*
 * Copyright 2019 Google Inc.
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
import com.google.common.primitives.Booleans;
import com.google.firebase.internal.Nullable;

public final class SendResponse {

  private final String messageId;
  private final FirebaseMessagingException exception;

  private SendResponse(String messageId, FirebaseMessagingException exception) {
    int argCount = Booleans.countTrue(!Strings.isNullOrEmpty(messageId), exception != null);
    checkArgument(argCount == 1, "Exactly one of messageId or exception must be specified");
    this.messageId = messageId;
    this.exception = exception;
  }

  @Nullable
  public String getMessageId() {
    return this.messageId;
  }

  @Nullable
  public FirebaseMessagingException getException() {
    return this.exception;
  }

  public boolean isSuccessful() {
    return !Strings.isNullOrEmpty(this.messageId);
  }

  static SendResponse fromMessageId(String messageId) {
    return new SendResponse(messageId, null);
  }

  static SendResponse fromException(FirebaseMessagingException exception) {
    return new SendResponse(null, exception);
  }
}
