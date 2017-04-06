package com.google.firebase.database;

import java.util.HashMap;
import java.util.Map;

/**
 * User: greg Date: 5/24/13 Time: 6:52 PM
 */
public class MapBuilder {

  private Map<String, Object> map = new HashMap<>();

  public MapBuilder put(String key, Object value) {
    map.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return map;
  }
}
