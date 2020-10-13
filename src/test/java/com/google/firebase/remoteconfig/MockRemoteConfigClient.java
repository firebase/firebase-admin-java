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

package com.google.firebase.remoteconfig;

public class MockRemoteConfigClient implements FirebaseRemoteConfigClient{

  private Template resultTemplate;
  private FirebaseRemoteConfigException exception;

  private MockRemoteConfigClient(Template resultTemplate,
                                 FirebaseRemoteConfigException exception) {
    this.resultTemplate = resultTemplate;
    this.exception = exception;
  }

  static MockRemoteConfigClient fromTemplate(Template resultTemplate) {
    return new MockRemoteConfigClient(resultTemplate, null);
  }

  static MockRemoteConfigClient fromException(FirebaseRemoteConfigException exception) {
    return new MockRemoteConfigClient(null, exception);
  }

  @Override
  public Template getTemplate() throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return resultTemplate;
  }
}
