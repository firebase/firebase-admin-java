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

import com.google.api.core.ApiFuture;

public interface ServerTemplate {
  public interface Builder {

    Builder defaultConfig(KeysAndValues config);

    Builder cachedTemplate(String templateJson);
    
    ServerTemplate build();
  }
  /**
  * Proccess the template data with a condition evaluator 
  * based on the provided context. 
  */
  ServerConfig evaluate(KeysAndValues context) throws FirebaseRemoteConfigException;
  /**
  * Proccess the template data without context.
  */
  ServerConfig evaluate() throws FirebaseRemoteConfigException;

  /**
  * Fetches and caches the current active version of the project.
  */
  ApiFuture<Void> load() throws FirebaseRemoteConfigException;

  String toJson();
}