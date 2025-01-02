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


