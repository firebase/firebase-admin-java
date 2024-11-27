package com.google.firebase.remoteconfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.google.common.collect.ImmutableMap;

public class ServerTemplate {
  private final FirebaseRemoteConfigClient rcClient;
  private ServerTemplateData cache;
  private ImmutableMap<String, String> stringifiedDefaultConfig;

  public ServerTemplate(FirebaseRemoteConfigClient rcClient, 
                        ImmutableMap<String, String> defaultConfig) {
    this.rcClient = rcClient;
    this.cache = null; 
    this.stringifiedDefaultConfig = defaultConfig != null ? defaultConfig : ImmutableMap.of();
  }

  public CompletableFuture<Void> load() {
    return CompletableFuture.supplyAsync(() -> {
        try {
            this.cache = rcClient.getServerTemplate();
            // Consider adding cache expiry logic here
            return null; // Since load() doesn't return a value
        } catch (FirebaseRemoteConfigException e) {
            // Handle the exception, e.g., complete the future exceptionally
            throw new CompletionException(e); 
        }
    });
}

/* 
  public ServerConfig evaluate(ImmutableMap<String, Object> context)
  throws FirebaseRemoteConfigException {
    // Evaluates the cached server template to produce a ServerConfig.
    if (this.cache == null) {
      throw new FirebaseRemoteConfigException("No Remote Config");
    }

    // ... (Logic to process the cached template into a ServerConfig)
    // You'll need to implement the evaluation logic based on your 
    // specific requirements and how ServerConfig is structured.

    // This is a placeholder for the actual evaluation logic.
    // Replace this with your code to process the template and context.
    ServerConfig config = new ServerConfig(/* ... ); 

    return config;
  }
*/
  public void set(ServerTemplateData template) {
    // Updates the cache to store the given template.
    this.cache = template;
  }
}