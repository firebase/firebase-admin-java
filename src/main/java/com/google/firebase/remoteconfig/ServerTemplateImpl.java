package com.google.firebase.remoteconfig;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ServerTemplateImpl implements ServerTemplate {

  private KeysAndValues defaultConfig;
  private String cachedTemplate;
  private FirebaseRemoteConfigClient client;
  private ServerTemplateData cache;

  public static class Builder implements ServerTemplate.Builder {
    private KeysAndValues defaultConfig;
    private ServerTemplateData cache;

    @Override
    public Builder defaultConfig(KeysAndValues config) {
      this.defaultConfig = config;
      return this;
    }

    @Override
    public Builder cachedTemplate(ServerTemplateData templateJson) {
      this.cache = templateJson;
      return this;
    }

    @Override
    public ServerTemplate build() {
      return new ServerTemplateImpl(this);
    }
  }

  private ServerTemplateImpl(Builder builder) {
    this.defaultConfig = builder.defaultConfig;
  }

  @Override
  public ServerConfig evaluate(KeysAndValues context) {
    // TODO: Implement evaluate with context
    // This should process the template with the given context
    // and return a ServerConfig object.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ServerConfig evaluate() {
    // TODO: Implement evaluate without context
    // This should process the template without any context
    // and return a ServerConfig object.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public ApiFuture<Void> load() throws FirebaseRemoteConfigException {
    this.cache = client.getServerTemplate();
    return ApiFutures.immediateFuture(null);
  }

  // Add getters or other methods as needed
  public KeysAndValues getDefaultConfig() {
    return defaultConfig;
  }

  public String getCachedTemplate() {
    return cachedTemplate;
  }

  public String toJson(ServerTemplateData serverTemplateData) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(serverTemplateData);
  }
}
