/*
 * Copyright 2017 Google Inc.
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
