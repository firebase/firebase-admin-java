/*
 * Copyright 2017 Google Inc.
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

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.internal.Nullable;
import java.util.Map;

/**
 * JSON data binding for JSON error messages sent by Google identity toolkit service. Message
 * format for these errors take the form `{"message": "code: optional details"}`.
 */
public final class AuthServiceErrorResponse {

  private static final Map<String, AuthErrorCode> ERROR_CODES =
      ImmutableMap.<String, AuthErrorCode>builder()
        .put("DUPLICATE_EMAIL", AuthErrorCode.EMAIL_ALREADY_EXISTS)
        .put("DUPLICATE_LOCAL_ID", AuthErrorCode.UID_ALREADY_EXISTS)
        .put("EMAIL_EXISTS", AuthErrorCode.EMAIL_ALREADY_EXISTS)
        .put("INVALID_DYNAMIC_LINK_DOMAIN", AuthErrorCode.INVALID_DYNAMIC_LINK_DOMAIN)
        .put("PHONE_NUMBER_EXISTS", AuthErrorCode.PHONE_NUMBER_ALREADY_EXISTS)
        .put("UNAUTHORIZED_DOMAIN", AuthErrorCode.UNAUTHORIZED_CONTINUE_URL)
        .put("USER_NOT_FOUND", AuthErrorCode.USER_NOT_FOUND)
        .build();

  private static final Map<String, String> ERROR_MESSAGES =
      ImmutableMap.<String, String>builder()
          .put("DUPLICATE_EMAIL", "The user with the provided email already exists")
          .put("DUPLICATE_LOCAL_ID", "The user with the provided uid already exists")
          .put("EMAIL_EXISTS", "The user with the provided email already exists")
          .put("INVALID_DYNAMIC_LINK_DOMAIN", "The provided dynamic link domain is not "
              + "configured or authorized for the current project.")
          .put("PHONE_NUMBER_EXISTS", "The user with the provided phone number already exists")
          .put("UNAUTHORIZED_DOMAIN", "The domain of the continue URL is not whitelisted. "
              + "Whitelist the domain in the Firebase console.")
          .put("USER_NOT_FOUND", "No user record found for the given identifier")
          .build();

  @Key("error")
  private Error error;

  @Nullable
  public AuthErrorCode getAuthErrorCode() {
    if (error != null) {
      return ERROR_CODES.get(error.getCode());
    }

    return null;
  }

  @Nullable
  public String getErrorMessage() {
    if (error == null) {
      return null;
    }

    String code = error.getCode();
    String message = ERROR_MESSAGES.get(code);
    if (Strings.isNullOrEmpty(message)) {
      return null;
    }

    String detail = error.getDetail();
    if (Strings.isNullOrEmpty(detail)) {
      return String.format("%s (%s).", message, code);
    }

    return String.format("%s (%s): %s", message, code, detail);
  }

  public static class Error {
    @Key("message")
    private String message;

    String getCode() {
      if (Strings.isNullOrEmpty(message)) {
        return null;
      }

      int separator = message.indexOf(':');
      if (separator != -1) {
        return message.substring(0, separator);
      }

      return message;
    }

    String getDetail() {
      if (Strings.isNullOrEmpty(message)) {
        return null;
      }

      int separator = message.indexOf(':');
      if (separator != -1) {
        return message.substring(separator + 1).trim();
      }

      return null;
    }
  }
}
