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
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.PlatformErrorHandler;
import java.io.IOException;

final class MessagingErrorHandler extends PlatformErrorHandler<FirebaseMessagingException> {

  MessagingErrorHandler(JsonFactory jsonFactory) {
    super(jsonFactory);
  }

  @Override
  protected FirebaseMessagingException createException(ErrorParams params) {
    return new FirebaseMessagingException(
        params.getErrorCode(),
        params.getMessage(),
        null, // TODO: Set FCM error code
        params.getException(),
        params.getResponse());
  }

  @Override
  public FirebaseMessagingException handleIOException(IOException e) {
    FirebaseException error = ApiClientUtils.newFirebaseException(e);
    return new FirebaseMessagingException(
        error.getPlatformErrorCode(),
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
}
