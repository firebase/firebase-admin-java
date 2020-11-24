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

/**
 * An interface for managing Firebase Remote Config templates.
 */
interface FirebaseRemoteConfigClient {

  /**
   * Gets the current active version of the Remote Config template.
   *
   * @return A {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while getting the template.
   */
  Template getTemplate() throws FirebaseRemoteConfigException;

  Template getTemplateAtVersion(String versionNumber) throws FirebaseRemoteConfigException;

  Template publishTemplate(Template template, boolean validateOnly,
                                  boolean forcePublish) throws FirebaseRemoteConfigException;

  Template rollback(String versionNumber) throws FirebaseRemoteConfigException;

  ListVersionsResponse listVersions(
          ListVersionsOptions options) throws FirebaseRemoteConfigException;
}
