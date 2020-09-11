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
                  .build();

  @Key("error")
  private Map<String, Object> error;

  public String getStatus() {
    if (error == null) {
      return null;
    }

    return (String) error.get("status");
  }

  @Nullable
  public RemoteConfigErrorCode getRemoteConfigErrorCode() {
    if (error == null) {
      return null;
    }

    String message = (String) error.get("message");
    if (Strings.isNullOrEmpty(message)) {
      return null;
    }

    int separator = message.indexOf(':');
    if (separator != -1) {
      String errorCode = message.substring(0, separator).replaceAll("\\[|\\]", "");
      return RC_ERROR_CODES.get(errorCode);
    }

    return null;
  }
}
