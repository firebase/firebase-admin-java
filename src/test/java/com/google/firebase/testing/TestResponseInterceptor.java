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

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Can be used to intercept HTTP requests and responses made by the SDK during tests.
 */
public class TestResponseInterceptor implements HttpResponseInterceptor {

  private List<HttpResponse> responses = new ArrayList<>();

  @Override
  public void interceptResponse(HttpResponse response) throws IOException {
    this.responses.add(response);
  }

  public HttpResponse getResponse() {
    if (responses.isEmpty()) {
      return null;
    }
    return Iterables.getLast(responses);
  }
}
