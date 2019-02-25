/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.Map;

class CountingLowLevelHttpRequest extends MockLowLevelHttpRequest {

  private final LowLevelHttpResponse response;
  private final IOException exception;
  private int count;

  private CountingLowLevelHttpRequest(LowLevelHttpResponse response, IOException exception) {
    this.response = response;
    this.exception = exception;
  }

  static CountingLowLevelHttpRequest fromStatus(int status) {
    return fromStatus(status, null);
  }

  static CountingLowLevelHttpRequest fromStatus(int status, Map<String, String> headers) {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
        .setStatusCode(status)
        .setZeroContent();
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        response.addHeader(entry.getKey(), entry.getValue());
      }
    }
    return fromStatus(response);
  }

  static CountingLowLevelHttpRequest fromStatus(LowLevelHttpResponse response) {
    return new CountingLowLevelHttpRequest(checkNotNull(response), null);
  }

  static CountingLowLevelHttpRequest fromException(IOException exception) {
    return new CountingLowLevelHttpRequest(null, checkNotNull(exception));
  }

  @Override
  public LowLevelHttpResponse execute() throws IOException {
    count++;
    if (response != null) {
      return response;
    }
    throw exception;
  }

  int getCount() {
    return count;
  }
}
