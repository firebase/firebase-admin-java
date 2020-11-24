/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.RemoteConfigErrorCode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The DTO for parsing error responses from the Remote Config service.
 * These error messages take the form,
 * `"error": {"code": 123, "message": "[CODE]: Message Details", "status": "ERROR_STATUS"}`
 */
public final class RemoteConfigServiceErrorResponse extends GenericJson {

  private static final Map<String, RemoteConfigErrorCode> RC_ERROR_CODES =
          ImmutableMap.<String, RemoteConfigErrorCode>builder()
                  .put("INTERNAL", RemoteConfigErrorCode.INTERNAL)
                  .put("INVALID_ARGUMENT", RemoteConfigErrorCode.INVALID_ARGUMENT)
                  .put("FAILED_PRECONDITION", RemoteConfigErrorCode.FAILED_PRECONDITION)
                  .put("UNAUTHENTICATED", RemoteConfigErrorCode.UNAUTHENTICATED)
                  .put("ALREADY_EXISTS", RemoteConfigErrorCode.ALREADY_EXISTS)
                  .put("VALIDATION_ERROR", RemoteConfigErrorCode.VALIDATION_ERROR)
                  .put("VERSION_MISMATCH", RemoteConfigErrorCode.VERSION_MISMATCH)
                  .build();

  private static final Pattern RC_ERROR_CODE_PATTERN = Pattern.compile("^\\[(\\w+)\\]:.*$");

  @Key("error")
  private Map<String, Object> error;

  @Nullable
  public RemoteConfigErrorCode getRemoteConfigErrorCode() {
    if (error == null) {
      return null;
    }

    String message = (String) error.get("message");
    if (Strings.isNullOrEmpty(message)) {
      return null;
    }

    Matcher errorMatcher = RC_ERROR_CODE_PATTERN.matcher(message);
    if (errorMatcher.find()) {
      String errorCode = errorMatcher.group(1);
      return RC_ERROR_CODES.get(errorCode);
    }

    return null;
  }
}
