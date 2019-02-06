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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.Nullable;

/**
 * The result of an individual send operation that was executed as part of a batch. See
 * {@link BatchResponse} for more details.
 */
public final class SendResponse {

  private final String messageId;
  private final FirebaseMessagingException exception;

  private SendResponse(String messageId, FirebaseMessagingException exception) {
    this.messageId = messageId;
    this.exception = exception;
  }

  /**
   * Returns a message ID string if the send operation was successful. Otherwise returns null.
   *
   * @return A message ID string or null.
   */
  @Nullable
  public String getMessageId() {
    return this.messageId;
  }

  /**
   * Returns an exception if the send operation failed. Otherwise returns null.
   *
   * @return A {@link FirebaseMessagingException} or null.
   */
  @Nullable
  public FirebaseMessagingException getException() {
    return this.exception;
  }

  /**
   * Returns whether the send operation was successful or not. When this method returns true,
   * {@link #getMessageId()} is guaranteed to return a non-null value. When this method returns
   * false {@link #getException()} is guaranteed to return a non-null value.
   *
   * @return A boolean indicating success of the operation.
   */
  public boolean isSuccessful() {
    return !Strings.isNullOrEmpty(this.messageId);
  }

  static SendResponse fromMessageId(String messageId) {
    checkArgument(!Strings.isNullOrEmpty(messageId), "messageId must not be null or empty");
    return new SendResponse(messageId, null);
  }

  static SendResponse fromException(FirebaseMessagingException exception) {
    checkNotNull(exception, "exception must not be null");
    return new SendResponse(null, exception);
  }
}
