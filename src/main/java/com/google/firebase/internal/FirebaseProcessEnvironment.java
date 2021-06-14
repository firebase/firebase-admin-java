/*
 * Copyright 2021 Google Inc.
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

package com.google.firebase.internal;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility for overriding environment variables during tests.
 */
public class FirebaseProcessEnvironment {

  private static final Map<String, String> localCache = new ConcurrentHashMap<>();

  public static String getenv(String name) {
    String cachedValue = localCache.get(name);
    if (!Strings.isNullOrEmpty(cachedValue)) {
      return  cachedValue;
    }

    return System.getenv(name);
  }

  public static void setenv(String name, String value) {
    localCache.put(name, value);
  }

  public static void clearCache() {
    localCache.clear();
  }
}
