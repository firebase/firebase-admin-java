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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;

/**
 * An abstract HttpErrorHandler that handles Google Cloud error responses. Format of these
 * error responses are defined at https://cloud.google.com/apis/design/errors.
 */
public abstract class AbstractPlatformErrorHandler<T extends FirebaseException>
    extends AbstractHttpErrorHandler<T> {

  protected final JsonFactory jsonFactory;

  public AbstractPlatformErrorHandler(JsonFactory jsonFactory) {
    this.jsonFactory = checkNotNull(jsonFactory, "jsonFactory must not be null");
  }

  @Override
  protected final FirebaseException httpResponseErrorToBaseException(
      HttpResponseException e, IncomingHttpResponse response) {
    FirebaseException base = super.httpResponseErrorToBaseException(e, response);
    PlatformErrorResponse parsedError = this.parseErrorResponse(e.getContent());

    ErrorCode code = base.getErrorCode();
    String status = parsedError.getStatus();
    if (!Strings.isNullOrEmpty(status)) {
      code = Enum.valueOf(ErrorCode.class, parsedError.getStatus());
    }

    String message = parsedError.getMessage();
    if (Strings.isNullOrEmpty(message)) {
      message = base.getMessage();
    }

    return new FirebaseException(code, message, e, response);
  }

  private PlatformErrorResponse parseErrorResponse(String content) {
    PlatformErrorResponse response = new PlatformErrorResponse();
    if (!Strings.isNullOrEmpty(content)) {
      try {
        jsonFactory.createJsonParser(content).parseAndClose(response);
      } catch (IOException e) {
        // Ignore any error that may occur while parsing the error response. The server
        // may have responded with a non-json payload. Return an empty return value, and
        // let the base class logic come into play.
      }
    }

    return response;
  }

  public static class PlatformErrorResponse {
    @Key("error")
    private PlatformError error;

    String getStatus() {
      return error != null ? error.status : null;
    }

    String getMessage() {
      return error != null ? error.message : null;
    }
  }

  public static class PlatformError {
    @Key("status")
    private String status;

    @Key("message")
    private String message;
  }
}
