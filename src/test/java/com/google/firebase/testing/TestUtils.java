package com.google.firebase.testing;

import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static com.google.firebase.internal.Preconditions.checkNotNull;
import static org.junit.Assert.fail;

import com.google.api.client.json.webtoken.JsonWebSignature;
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

  /**
   * Deeply compares two (2) objects. This method will call any overridden equals() methods if they
   * exist. If not, it will then proceed to do a field-by-field comparison, and when a non-primitive
   * field is encountered, recursively continue the deep comparison. When an array is found, it will
   * also ensure that the array contents are deeply equal, not requiring the array instance
   * (container) to be identical. This method will successfully compare object graphs that have
   * cycles (A->B->C->A). There is no need to ever use the Arrays.deepEquals() method as this is
   * a true and more effective super set.
   */
  public static void assertDeepEquals(Object a, Object b) {
    if (!deepEquals(a, b)) {
      fail("Values different.\nExpected: " + a + "\nActual: " + b);
    }
  }

  static String loadResource(String path) {
    InputStream stream = TestUtils.class.getClassLoader().getResourceAsStream(path);
    checkNotNull(stream, "Failed to load resource: " + path);
    try (InputStreamReader reader = new InputStreamReader(stream)) {
      return CharStreams.toString(reader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
