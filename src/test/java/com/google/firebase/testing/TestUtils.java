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

package com.google.firebase.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test Utils for use by all tests (both unit and integration tests). */
public class TestUtils {

  public static final long TEST_TIMEOUT_MILLIS = 7 * 1000;

  public static boolean verifySignature(JsonWebSignature token, List<PublicKey> keys)
      throws Exception {
    for (PublicKey key : keys) {
      if (token.verifySignature(key)) {
        return true;
      }
    }
    return false;
  }

  public static void setEnvironmentVariables(Map<String, String> vars) {
    // Setting the environment variables after the JVM has started requires a bit of a hack:
    // we reach into the package-private java.lang.ProcessEnvironment class, which incidentally
    // is platform-specific, and replace the map held in a static final field there,
    // using yet more reflection.
    //
    // This is copied from {#see com.google.apphosting.runtime.NullSandboxPlugin}
    Map<String, String> allVars = new HashMap<>(System.getenv());
    allVars.putAll(vars);
    try {
      Class<?> pe = Class.forName("java.lang.ProcessEnvironment", true, null);
      Field f = pe.getDeclaredField("theUnmodifiableEnvironment");
      f.setAccessible(true);
      Field m = Field.class.getDeclaredField("modifiers");
      m.setAccessible(true);
      m.setInt(f, m.getInt(f) & ~Modifier.FINAL);
      f.set(null, Collections.unmodifiableMap(allVars));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("failed to set the environment variables", e);
    }
  }

  public static String loadResource(String path) {
    InputStream stream = TestUtils.class.getClassLoader().getResourceAsStream(path);
    checkNotNull(stream, "Failed to load resource: %s", path);
    try (InputStreamReader reader = new InputStreamReader(stream)) {
      return CharStreams.toString(reader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static GoogleCredentials getCertCredential(InputStream stream) {
    try {
      return GoogleCredentials.fromStream(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
