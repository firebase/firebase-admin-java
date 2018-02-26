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

package com.google.firebase.testing;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/**
 * A mock HttpTransport that can simulate multiple (sequential) HTTP interactions. This can be
 * used when an SDK operation makes multiple backend calls.
 */
public class MultiRequestMockHttpTransport extends MockHttpTransport {

  private final List<MockLowLevelHttpResponse> responses;
  private int index = 0;

  public MultiRequestMockHttpTransport(List<MockLowLevelHttpResponse> responses) {
    this.responses = ImmutableList.copyOf(responses);
  }

  @Override
  public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
    LowLevelHttpRequest request = super.buildRequest(method, url);
    ((MockLowLevelHttpRequest) request).setResponse(responses.get(index++));
    return request;
  }
}
