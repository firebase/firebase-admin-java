/*
 * Copyright 2020 Google Inc.
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

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;
import java.util.Map;

/**
 * An abstract HttpErrorHandler implementation that maps HTTP status codes to Firebase error codes.
 */
public abstract class AbstractHttpErrorHandler<T extends FirebaseException>
    implements HttpErrorHandler<T> {

  private static final Map<Integer, ErrorCode> HTTP_ERROR_CODES =
      ImmutableMap.<Integer, ErrorCode>builder()
          .put(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, ErrorCode.INVALID_ARGUMENT)
          .put(HttpStatusCodes.STATUS_CODE_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED)
          .put(HttpStatusCodes.STATUS_CODE_FORBIDDEN, ErrorCode.PERMISSION_DENIED)
          .put(HttpStatusCodes.STATUS_CODE_NOT_FOUND, ErrorCode.NOT_FOUND)
          .put(HttpStatusCodes.STATUS_CODE_CONFLICT, ErrorCode.CONFLICT)
          .put(429, ErrorCode.RESOURCE_EXHAUSTED)
          .put(HttpStatusCodes.STATUS_CODE_SERVER_ERROR, ErrorCode.INTERNAL)
          .put(HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE, ErrorCode.UNAVAILABLE)
          .build();

  @Override
  public final T handleHttpResponseException(
      HttpResponseException e, IncomingHttpResponse response) {
    ErrorParams params = getErrorParams(e, response);
    return this.createException(params);
  }

  protected ErrorParams getErrorParams(HttpResponseException e, IncomingHttpResponse response) {
    ErrorCode code = HTTP_ERROR_CODES.get(e.getStatusCode());
    if (code == null) {
      code = ErrorCode.UNKNOWN;
    }

    String message = String.format("Unexpected HTTP response with status: %d\n%s",
        e.getStatusCode(), e.getContent());
    return new ErrorParams(code, message, e, response);
  }

  protected abstract T createException(ErrorParams params);

  public static final class ErrorParams {

    private final ErrorCode errorCode;
    private final String message;
    private final HttpResponseException exception;
    private final IncomingHttpResponse response;

    public ErrorParams(
        ErrorCode errorCode,
        String message,
        @Nullable HttpResponseException e,
        @Nullable IncomingHttpResponse response) {

      this.errorCode = checkNotNull(errorCode, "errorCode must not be null");
      checkArgument(!Strings.isNullOrEmpty(message), "message must not be null or empty");
      this.message = message;
      this.exception = e;
      this.response = response;
    }

    public ErrorCode getErrorCode() {
      return errorCode;
    }

    public String getMessage() {
      return message;
    }

    @Nullable
    public HttpResponseException getException() {
      return exception;
    }

    @Nullable
    public IncomingHttpResponse getResponse() {
      return response;
    }
  }
}
