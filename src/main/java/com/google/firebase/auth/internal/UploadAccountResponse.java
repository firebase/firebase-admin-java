/*
 * Copyright 2018 Google Inc.
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
import java.util.List;

/**
 * Represents the response from identity toolkit for a user import request.
 */
public class UploadAccountResponse {

  @Key("error")
  private List<ErrorInfo> errors;

  public List<ErrorInfo> getErrors() {
    return errors;
  }

  public static class ErrorInfo {
    @Key("index")
    private int index;

    @Key("message")
    private String message;

    public int getIndex() {
      return index;
    }

    public String getMessage() {
      return message;
    }
  }
}
