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

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.firebase.internal.Nullable;

/**
 * JSON data binding for JSON error messages sent by Google identity toolkit service. These
 * error messages take the form `{"error": {"message": "CODE: OPTIONAL DETAILS"}}`.
 */
public final class AuthServiceErrorResponse {

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
