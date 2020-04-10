/*
 * Copyright  2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;

import java.util.List;

/**
 * Response from an operation that sends FCM messages to multiple recipients.
 * See {@link FirebaseMessaging#sendAll(List)} and {@link
 * FirebaseMessaging#sendMulticast(MulticastMessage)}.
 */
class BatchResponseImpl implements BatchResponse {

  private final List<SendResponse> responses;
  private final int successCount;

  BatchResponseImpl(List<SendResponse> responses) {
    this.responses = ImmutableList.copyOf(responses);
    int successCount = 0;
    for (SendResponse response : this.responses) {
      if (response.isSuccessful()) {
        successCount++;
      }
    }
    this.successCount = successCount;
  }

  @NonNull
  public List<SendResponse> getResponses() {
    return responses;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getFailureCount() {
    return responses.size() - successCount;
  }

}
