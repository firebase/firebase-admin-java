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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.internal.AbstractHttpErrorHandler;
import com.google.firebase.internal.Nullable;
import java.io.IOException;
import java.util.Map;

final class AuthErrorHandler extends AbstractHttpErrorHandler<FirebaseAuthException> {

  private static final Map<String, AuthError> ERROR_CODES =
      ImmutableMap.<String, AuthError>builder()
          .put(
              "CONFIGURATION_NOT_FOUND",
              new AuthError(
                  ErrorCode.NOT_FOUND,
                  "No IdP configuration found corresponding to the provided identifier",
                  AuthErrorCode.CONFIGURATION_NOT_FOUND))
          .put(
              "DUPLICATE_EMAIL",
              new AuthError(
                  ErrorCode.ALREADY_EXISTS,
                  "The user with the provided email already exists",
                  AuthErrorCode.EMAIL_ALREADY_EXISTS))
          .put(
              "DUPLICATE_LOCAL_ID",
              new AuthError(
                  ErrorCode.ALREADY_EXISTS,
                  "The user with the provided uid already exists",
                  AuthErrorCode.UID_ALREADY_EXISTS))
          .put(
              "EMAIL_EXISTS",
              new AuthError(
                  ErrorCode.ALREADY_EXISTS,
                  "The user with the provided email already exists",
                  AuthErrorCode.EMAIL_ALREADY_EXISTS))
          .put(
              "INVALID_DYNAMIC_LINK_DOMAIN",
              new AuthError(
                  ErrorCode.INVALID_ARGUMENT,
                  "The provided dynamic link domain is not "
                      + "configured or authorized for the current project",
                  AuthErrorCode.INVALID_DYNAMIC_LINK_DOMAIN))
          .put(
              "PHONE_NUMBER_EXISTS",
              new AuthError(
                  ErrorCode.ALREADY_EXISTS,
                  "The user with the provided phone number already exists",
                  AuthErrorCode.PHONE_NUMBER_ALREADY_EXISTS))
          .put(
              "TENANT_NOT_FOUND",
              new AuthError(
                  ErrorCode.NOT_FOUND,
                  "No tenant found for the given identifier",
                  AuthErrorCode.TENANT_NOT_FOUND))
          .put(
              "UNAUTHORIZED_DOMAIN",
              new AuthError(
                  ErrorCode.INVALID_ARGUMENT,
                  "The domain of the continue URL is not whitelisted",
                  AuthErrorCode.UNAUTHORIZED_CONTINUE_URL))
          .put(
              "USER_NOT_FOUND",
              new AuthError(
                  ErrorCode.NOT_FOUND,
                  "No user record found for the given identifier",
                  AuthErrorCode.USER_NOT_FOUND))
          .build();

  private final JsonFactory jsonFactory;

  AuthErrorHandler(JsonFactory jsonFactory) {
    this.jsonFactory = checkNotNull(jsonFactory);
  }

  @Override
  protected FirebaseAuthException createException(FirebaseException base) {
    String response = getResponse(base);
    AuthServiceErrorResponse parsed = safeParse(response);
    AuthError errorInfo = ERROR_CODES.get(parsed.getCode());
    if (errorInfo != null) {
      return new FirebaseAuthException(
          errorInfo.getErrorCode(),
          errorInfo.buildMessage(parsed),
          base.getCause(),
          base.getHttpResponse(),
          errorInfo.getAuthErrorCode());
    }

    return new FirebaseAuthException(base);
  }

  private String getResponse(FirebaseException base) {
    if (base.getHttpResponse() == null) {
      return null;
    }

    return base.getHttpResponse().getContent();
  }

  private AuthServiceErrorResponse safeParse(String response) {
    AuthServiceErrorResponse parsed = new AuthServiceErrorResponse();
    if (!Strings.isNullOrEmpty(response)) {
      try {
        jsonFactory.createJsonParser(response).parse(parsed);
      } catch (IOException ignore) {
        // Ignore any error that may occur while parsing the error response. The server
        // may have responded with a non-json payload.
      }
    }

    return parsed;
  }

  private static class AuthError {

    private final ErrorCode errorCode;
    private final String message;
    private final AuthErrorCode authErrorCode;

    AuthError(ErrorCode errorCode, String message, AuthErrorCode authErrorCode) {
      this.errorCode = errorCode;
      this.message = message;
      this.authErrorCode = authErrorCode;
    }

    ErrorCode getErrorCode() {
      return errorCode;
    }

    AuthErrorCode getAuthErrorCode() {
      return authErrorCode;
    }

    String buildMessage(AuthServiceErrorResponse response) {
      StringBuilder builder = new StringBuilder(this.message)
          .append(" (").append(response.getCode()).append(")");
      String detail = response.getDetail();
      if (!Strings.isNullOrEmpty(detail)) {
        builder.append(": ").append(detail);
      } else {
        builder.append(".");
      }

      return builder.toString();
    }
  }

  /**
   * JSON data binding for JSON error messages sent by Google identity toolkit service. These
   * error messages take the form `{"error": {"message": "CODE: OPTIONAL DETAILS"}}`.
   */
  private static class AuthServiceErrorResponse {

    @Key("error")
    private GenericJson error;

    @Nullable
    public String getCode() {
      String message = getMessage();
      if (Strings.isNullOrEmpty(message)) {
        return null;
      }

      int separator = message.indexOf(':');
      if (separator != -1) {
        return message.substring(0, separator);
      }

      return message;
    }

    @Nullable
    public String getDetail() {
      String message = getMessage();
      if (Strings.isNullOrEmpty(message)) {
        return null;
      }

      int separator = message.indexOf(':');
      if (separator != -1) {
        return message.substring(separator + 1).trim();
      }

      return null;
    }

    private String getMessage() {
      if (error == null) {
        return null;
      }

      return (String) error.get("message");
    }
  }
}
