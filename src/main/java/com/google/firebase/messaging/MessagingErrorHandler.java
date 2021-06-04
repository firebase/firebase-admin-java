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

import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.PlatformErrorHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;

final class MessagingErrorHandler extends PlatformErrorHandler<FirebaseMessagingException> {

  private static final String MESSAGING_ERROR_TYPE =
      "type.googleapis.com/google.firebase.fcm.v1.FcmError";

  private static final Map<String, MessagingErrorCode> MESSAGING_ERROR_CODES =
      ImmutableMap.<String, MessagingErrorCode>builder()
          .put("APNS_AUTH_ERROR", MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)
          .put("INTERNAL", MessagingErrorCode.INTERNAL)
          .put("INVALID_ARGUMENT", MessagingErrorCode.INVALID_ARGUMENT)
          .put("QUOTA_EXCEEDED", MessagingErrorCode.QUOTA_EXCEEDED)
          .put("SENDER_ID_MISMATCH", MessagingErrorCode.SENDER_ID_MISMATCH)
          .put("THIRD_PARTY_AUTH_ERROR", MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)
          .put("UNAVAILABLE", MessagingErrorCode.UNAVAILABLE)
          .put("UNREGISTERED", MessagingErrorCode.UNREGISTERED)
          .build();

  MessagingErrorHandler(JsonFactory jsonFactory) {
    super(jsonFactory);
  }

  @Override
  protected FirebaseMessagingException createException(ErrorParams params) {
    String content = params.getResponse().getContent();
    return new FirebaseMessagingException(
        params.getErrorCode(),
        params.getMessage(),
        getMessagingErrorCode(content),
        params.getException(),
        params.getResponse());
  }

  @Override
  public FirebaseMessagingException handleIOException(IOException e) {
    FirebaseException error = ApiClientUtils.newFirebaseException(e);
    return new FirebaseMessagingException(
        error.getCode(),
        error.getMessage(),
        null,
        e,
        null);
  }

  @Override
  public FirebaseMessagingException handleParseException(IOException e,
      FirebaseHttpResponse response) {
    return new FirebaseMessagingException(
        ErrorCode.UNKNOWN,
        "Error parsing response from FCM: " + e.getMessage(),
        null,
        e,
        response);
  }

  private MessagingErrorCode getMessagingErrorCode(String content) {
    if (content == null) {
      return null;
    }

    MessagingErrorResponse response;
    try {
      response = jsonFactory.createJsonParser(content).parseAndClose(MessagingErrorResponse.class);
    } catch (IOException e) {
      // Ignore any error that may occur while parsing the error response. The server
      // may have responded with a non-json payload. Return an empty return value, and
      // let the base class logic come into play.
      return null;
    }

    if (response.error == null || response.error.details == null) {
      return null;
    }

    List<MessagingErrorDetail> details = response.error.details;
    for (MessagingErrorDetail detail : details) {
      if (MESSAGING_ERROR_TYPE.equals(detail.type)) {
        return MESSAGING_ERROR_CODES.get(detail.errorCode);
      }
    }

    return null;
  }

  public static class MessagingErrorResponse {
    @Key("error")
    private MessagingError error;
  }

  public static class MessagingError extends PlatformErrorHandler.PlatformError {
    @Key("details")
    private List<MessagingErrorDetail> details;
  }

  public static class MessagingErrorDetail {
    @Key("@type")
    private String type;

    @Key("errorCode")
    private String errorCode;
  }
}
