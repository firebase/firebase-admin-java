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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.List;
import java.util.Map;

/**
 * The response produced by FCM topic management operations.
 */
public class TopicManagementResponse {

  private static final String UNKNOWN_ERROR = "unknown-error";

  // Server error codes as defined in https://developers.google.com/instance-id/reference/server
  // TODO: Should we handle other error codes here (e.g. PERMISSION_DENIED)?
  private static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("INVALID_ARGUMENT", "invalid-argument")
      .put("NOT_FOUND", "registration-token-not-registered")
      .put("INTERNAL", "internal-error")
      .put("TOO_MANY_TOPICS", "too-many-topics")
      .build();

  private final int successCount;
  private final List<Error> errors;

  TopicManagementResponse(List<Map<String, Object>> results) {
    int successCount = 0;
    ImmutableList.Builder<Error> errors = ImmutableList.builder();
    for (int i = 0; i < results.size(); i++) {
      Map result = results.get(i);
      if (result.isEmpty()) {
        successCount++;
      } else {
        errors.add(new Error(i, (String) result.get("error")));
      }
    }
    this.successCount = successCount;
    this.errors = errors.build();
  }

  /**
   * Gets the number of registration tokens that were successfully subscribed or unsubscribed.
   *
   * @return The number of successes.
   */
  public int getSuccessCount() {
    return successCount;
  }

  /**
   * Gets the number of registration tokens that could not be subscribed or unsubscribed, and
   * resulted in an error.
   *
   * @return The number of failures.
   */
  public int getFailureCount() {
    return errors.size();
  }

  /**
   * Gets a list of errors encountered while executing the topic management operation.
   *
   * @return A non-null list.
   */
  @NonNull
  public List<Error> getErrors() {
    return errors;
  }

  /**
   * A topic management error.
   */
  public static class Error {
    private final int index;
    private final String reason;

    private Error(int index, String reason) {
      this.index = index;
      this.reason = ERROR_CODES.containsKey(reason)
        ? ERROR_CODES.get(reason) : UNKNOWN_ERROR;
    }

    /**
     * Index of the registration token to which this error is related to.
     *
     * @return An index into the original registration token list.
     */
    public int getIndex() {
      return index;
    }

    /**
     * String describing the nature of the error.
     *
     * @return A non-null, non-empty error message.
     */
    @NonNull
    public String getReason() {
      return reason;
    }
  }
}
