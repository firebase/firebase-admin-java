package com.google.firebase.database;

import java.util.HashMap;
import java.util.Map;

public class MapBuilder {

  private Map<String, Object> map = new HashMap<>();

  public MapBuilder put(String key, Object value) {
    map.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return map;
  }

  public static Map<String, Object> of(String k1, Object v1) {
    return new MapBuilder().put(k1, v1).build();
  }

  public static Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
    return new MapBuilder().put(k1, v1).put(k2, v2).build();
  }

  public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3,
                                       Object v3) {
    return new MapBuilder().put(k1, v1).put(k2, v2).put(k3, v3).build();
  }
}
