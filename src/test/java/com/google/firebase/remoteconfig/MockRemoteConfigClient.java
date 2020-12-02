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

import com.google.firebase.remoteconfig.internal.TemplateResponse.ListVersionsResponse;

public class MockRemoteConfigClient implements FirebaseRemoteConfigClient{

  private final Template resultTemplate;
  private final FirebaseRemoteConfigException exception;
  private final ListVersionsResponse listVersionsResponse;

  private MockRemoteConfigClient(Template resultTemplate,
                                 ListVersionsResponse listVersionsResponse,
                                 FirebaseRemoteConfigException exception) {
    this.resultTemplate = resultTemplate;
    this.listVersionsResponse = listVersionsResponse;
    this.exception = exception;
  }

  static MockRemoteConfigClient fromTemplate(Template resultTemplate) {
    return new MockRemoteConfigClient(resultTemplate, null, null);
  }

  static MockRemoteConfigClient fromListVersionsResponse(
          ListVersionsResponse listVersionsResponse) {
    return new MockRemoteConfigClient(null, listVersionsResponse, null);
  }

  static MockRemoteConfigClient fromException(FirebaseRemoteConfigException exception) {
    return new MockRemoteConfigClient(null, null, exception);
  }

  @Override
  public Template getTemplate() throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return resultTemplate;
  }

  @Override
  public Template getTemplateAtVersion(String versionNumber) throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return resultTemplate;
  }

  @Override
  public Template publishTemplate(Template template, boolean validateOnly,
                                  boolean forcePublish) throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return resultTemplate;
  }

  @Override
  public Template rollback(String versionNumber) throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return resultTemplate;
  }

  @Override
  public ListVersionsResponse listVersions(
          ListVersionsOptions options) throws FirebaseRemoteConfigException {
    if (exception != null) {
      throw exception;
    }
    return listVersionsResponse;
  }
}
