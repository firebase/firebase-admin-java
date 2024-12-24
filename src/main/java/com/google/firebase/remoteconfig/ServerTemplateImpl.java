package com.google.firebase.remoteconfig;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ServerTemplateImpl implements ServerTemplate {

  private KeysAndValues defaultConfig;
  private FirebaseRemoteConfigClient client;
  private ServerTemplateData cache;
  private String cachedTemplate; // Added field for cached template


  public static class Builder implements ServerTemplate.Builder {
    private KeysAndValues defaultConfig;
    private String cachedTemplate;
    private FirebaseRemoteConfigClient client;
    
    @Override
    public Builder defaultConfig(KeysAndValues config) {
      this.defaultConfig = config;
      System.out.println(this.defaultConfig);
      return this;
    }

    @Override
    public Builder cachedTemplate(String templateJson) {
      this.cachedTemplate = templateJson;
      System.out.println(this.cachedTemplate);
      return this;
    }

    @Override
    public Builder addClient(FirebaseRemoteConfigClient client) {
      System.out.println("Inside client");
      this.client = client;
      return this;
    }

    @Override
    public ServerTemplate build() {
      return new ServerTemplateImpl(this);
    }

    // Added getter for cache
    public ServerTemplateData getCache() {
      try {
        System.out.println("inside getCache");
        return ServerTemplateData.fromJSON(cachedTemplate);
      } catch (FirebaseRemoteConfigException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }
  }

  private ServerTemplateImpl(Builder builder) {
    System.out.println("Successfully cache template");
    this.defaultConfig = builder.defaultConfig;
    this.cache = builder.getCache(); 
    this.client = builder.client;// Initialize cache from builder
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
    System.out.println("Inside load");
    this.cachedTemplate = client.getServerTemplate(); 
    System.out.println(cachedTemplate);
    this.cache = ServerTemplateData.fromJSON(cachedTemplate);
    // Assuming getServerTemplate() now returns ServerTemplateData
    // this.cache = serverTemplateData.setETag(getETag(response)); 
    // Remove this line, as getETag(response) is not defined
    return ApiFutures.immediateFuture(null);
  }

  // Add getters or other methods as needed
  public KeysAndValues getDefaultConfig() {
    return defaultConfig;
  }

  public String getCachedTemplate() {
    return cachedTemplate;
  }

  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(this.cache);
  }
}