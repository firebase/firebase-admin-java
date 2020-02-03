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

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An abstract HttpErrorHandler implementation that maps HTTP status codes to Firebase error codes.
 * Also provides reasonable default implementations to other error handler methods in the
 * HttpErrorHandler interface.
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
    FirebaseException base = this.httpResponseErrorToBaseException(e, response);
    return this.createException(base);
  }

  @Override
  public final T handleIOException(IOException e) {
    FirebaseException base = this.ioErrorToBaseException(e);
    return this.createException(base);
  }

  @Override
  public final T handleParseException(IOException e, IncomingHttpResponse response) {
    FirebaseException base = this.parseErrorToBaseException(e, response);
    return this.createException(base);
  }

  /**
   * Creates a FirebaseException from the given HTTP response error. Error code is determined from
   * the HTTP status code of the response. Error message includes both the status code and full
   * response payload to aid in debugging.
   *
   * @param e HTTP response exception.
   * @param response Incoming HTTP response.
   * @return A FirebaseException instance.
   */
  protected FirebaseException httpResponseErrorToBaseException(
      HttpResponseException e, IncomingHttpResponse response) {
    ErrorCode code = HTTP_ERROR_CODES.get(e.getStatusCode());
    if (code == null) {
      code = ErrorCode.UNKNOWN;
    }

    String message = String.format("Unexpected HTTP response with status: %d\n%s",
        e.getStatusCode(), e.getContent());
    return new FirebaseException(code, message, e, response);
  }

  /**
   * Creates a FirebaseException from the given IOException. If IOException resulted from a socket
   * timeout, sets the error code DEADLINE_EXCEEDED. If the IOException resulted from a network
   * outage or other connectivity issue, sets the error code to UNAVAILABLE. In all other cases sets
   * the error code to UNKNOWN.
   *
   * @param e IOException to create the new exception from.
   * @return A FirebaseException instance.
   */
  protected FirebaseException ioErrorToBaseException(IOException e) {
    ErrorCode code = ErrorCode.UNKNOWN;
    String message = "Unknown error while making a remote service call" ;
    if (isInstance(e, SocketTimeoutException.class)) {
      code = ErrorCode.DEADLINE_EXCEEDED;
      message = "Timed out while making an API call";
    }

    if (isInstance(e, UnknownHostException.class) || isInstance(e, NoRouteToHostException.class)) {
      code = ErrorCode.UNAVAILABLE;
      message = "Failed to establish a connection";
    }

    return new FirebaseException(code, message + ": " + e.getMessage(), e);
  }

  protected FirebaseException parseErrorToBaseException(
      IOException e, IncomingHttpResponse response) {
    return new FirebaseException(
        ErrorCode.UNKNOWN, "Error while parsing HTTP response: " + e.getMessage(), e, response);
  }

  /**
   * Converts the given base FirebaseException to a more specific exception type. The base exception
   * is guaranteed to have an error code, a message and a cause. But the HTTP response is only set
   * if the exception occurred after receiving a response from a remote server.
   *
   * @param base A FirebaseException.
   * @return A more specific exception created from the base.
   */
  protected abstract T createException(FirebaseException base);

  /**
   * Checks if the given exception stack t contains an instance of type.
   */
  private <U> boolean isInstance(IOException t, Class<U> type) {
    Throwable current = t;
    Set<Throwable> chain = new HashSet<>();
    while (current != null) {
      if (!chain.add(current)) {
        break;
      }

      if (type.isInstance(current)) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }
}
