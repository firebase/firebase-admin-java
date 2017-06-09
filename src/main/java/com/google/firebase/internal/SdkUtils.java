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

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A collection of internal utilities that can be used anywhere in the Admin SDK.
 */
public class SdkUtils {

  private static final String ADMIN_SDK_PROPERTIES = "admin_sdk.properties";
  private static final String SDK_VERSION = loadSdkVersion();

  /**
   * Returns the version of this Admin SDK distribution.
   *
   * @return A semver version string.
   */
  public static String getVersion() {
    return SDK_VERSION;
  }

  private static String loadSdkVersion() {
    try (InputStream in = SdkUtils.class.getClassLoader()
        .getResourceAsStream(ADMIN_SDK_PROPERTIES)) {
      Properties properties = new Properties();
      properties.load(checkNotNull(in, "Failed to load: " + ADMIN_SDK_PROPERTIES));
      return properties.getProperty("sdk.version");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
