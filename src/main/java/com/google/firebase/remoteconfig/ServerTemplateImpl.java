
/*
 * Copyright 2025 Google LLC
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
import com.google.api.core.ApiFutures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.atomic.AtomicReference;

public final class ServerTemplateImpl implements ServerTemplate {

  private final KeysAndValues defaultConfig;
  private FirebaseRemoteConfigClient client;
  private ServerTemplateData cache;
  private final AtomicReference<String> cachedTemplate; // Added field for cached template
  
  public static class Builder implements ServerTemplate.Builder {
    private KeysAndValues defaultConfig;
    private String cachedTemplate;
    private FirebaseRemoteConfigClient client;
  
    Builder(FirebaseRemoteConfigClient remoteConfigClient) {
      this.client = remoteConfigClient;
    }

    @Override
    public Builder defaultConfig(KeysAndValues config) {
      this.defaultConfig = config;
      return this;
    }

    @Override
    public Builder cachedTemplate(String templateJson) {
      this.cachedTemplate = templateJson;
      return this;
    }

    @Override
    public ServerTemplate build() {
      return new ServerTemplateImpl(this);
    }
  }

  private ServerTemplateImpl(Builder builder) {
    this.defaultConfig = builder.defaultConfig;
    this.cachedTemplate = new AtomicReference<>(builder.cachedTemplate);
    this.client = builder.client;
    try {
      this.cache = ServerTemplateData.fromJSON(this.cachedTemplate.get());
    } catch (FirebaseRemoteConfigException e) {
      e.printStackTrace();
    }
  }

  @Override
  public ApiFuture<Void> load() throws FirebaseRemoteConfigException {
    String serverTemplate = client.getServerTemplate();
    this.cachedTemplate.set(serverTemplate);
    this.cache = ServerTemplateData.fromJSON(serverTemplate);
    return ApiFutures.immediateFuture(null);
  }

  // Add getters or other methods as needed
  public KeysAndValues getDefaultConfig() {
    return defaultConfig;
  }

  public String getCachedTemplate() {
    return cachedTemplate.get();
  }

  @Override
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(this.cache);
  }
}

