package com.google.firebase.database.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.testing.TestUtils;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class EmulatorHelperTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testExtractingEmulatorUrlFromSuppliedUrlSucceeds() {
    Map<String, String> suppliedToExpectedUrlsMap = ImmutableMap.of(
        "http://localhost:9000?ns=test-ns", "http://localhost:9000/?ns=test-ns",
        "http://my-custom-hosted-emulator.com:80?ns=dummy-ns",
        "http://my-custom-hosted-emulator.com:80/?ns=dummy-ns",
        "http://my-custom-hosted-emulator.com:80/path/to/document?ns=dummy-ns",
        "http://my-custom-hosted-emulator.com:80/path/to/document/?ns=dummy-ns"
    );
    for (Map.Entry<String, String> e : suppliedToExpectedUrlsMap.entrySet()) {
      assertEquals(e.getValue(), EmulatorHelper.extractEmulatorUrlFromDbUrl(e.getKey()));
    }
  }

  @Test
  public void testExtractingEmulatorUrlsFromSuppliedUrlFails() {
    List<String> nonEmulatorUrls = ImmutableList.of(
        "https://localhost:9000?ns=test-ns",// https scheme
        "http://localhost?ns=test-ns", // missing port
        "http://localhost:9000",// missing ns param
        "http://my-custom-hosted-emulator.com?ns=dummy-ns", // missing port
        "http://my-custom-hosted-emulator.com:80", // missing ns param
        "http://test-namespace.firebaseio.com" // firebaseio.com not supported
    );
    for (String url : nonEmulatorUrls) {
      assertNull(EmulatorHelper.extractEmulatorUrlFromDbUrl(url));
    }
  }

  @Test
  public void testExtractingEmulatorUrlsThrowsException() {
    List<String> invalidFormedUrls = ImmutableList.of(
        "localhost",
        "localhost:9000"
    );
    for (String invalidFormedUrl : invalidFormedUrls) {
      thrown.expect(DatabaseException.class);
      EmulatorHelper.extractEmulatorUrlFromDbUrl(invalidFormedUrl);
    }
  }

  @Test
  public void testEmulatorUrlCorrectlyPickedUp() {
    class CustomTestCase {

      private String suppliedDbUrl;
      private String envVariableUrl;
      private String expectedEmulatorUrl;

      private CustomTestCase(String suppliedDbUrl, String envVariableUrl,
          String expectedEmulatorUrl) {
        this.suppliedDbUrl = suppliedDbUrl;
        this.envVariableUrl = envVariableUrl;
        this.expectedEmulatorUrl = expectedEmulatorUrl;
      }
    }

    List<CustomTestCase> testCases; // separated declaration and assignment coz of checkstyle
    testCases = ImmutableList.of(
        // cases where the env var is ignored because the supplied DB URL is a valid emulator URL
        new CustomTestCase("http://my-custom-hosted-emulator.com:80?ns=dummy-ns", "",
            "http://my-custom-hosted-emulator.com:80/?ns=dummy-ns"),
        new CustomTestCase("http://localhost:9000?ns=test-ns", null,
            "http://localhost:9000/?ns=test-ns"),
        new CustomTestCase("http://my-custom-hosted-emulator.com:80?ns=dummy-ns",
            "http://localhost:8080/ns=ns-2",
            "http://my-custom-hosted-emulator.com:80/?ns=dummy-ns"),
        new CustomTestCase("http://localhost:9000?ns=ns-1", "localhost:8080",
            "http://localhost:9000/?ns=ns-1"),
        new CustomTestCase("http://localhost:9000?ns=ns-1", "http://localhost:8080/ns=ns-2",
            "http://localhost:9000/?ns=ns-1"),
        new CustomTestCase("http://localhost:9000/a/b/c?ns=ns-1", "http://localhost:8080/ns=ns-2",
            "http://localhost:9000/a/b/c/?ns=ns-1"),

        // cases where the supplied DB URL is not an emulator URL, so we extract ns from it
        // and append it to the emulator URL from env var(if it is valid)
        new CustomTestCase("https://valid-namespace.firebaseio.com", "localhost:8080",
            "http://localhost:8080/?ns=valid-namespace"),
        new CustomTestCase("https://valid-namespace.firebaseio.com", "custom-emulator-url:90",
            "http://custom-emulator-url:90/?ns=valid-namespace"),
        new CustomTestCase("https://valid-namespace.firebaseio.com/a/b/c", "custom-emulator-url:90",
            "http://custom-emulator-url:90/a/b/c/?ns=valid-namespace"),
        new CustomTestCase("https://valid-namespace.firebaseio.com", "192.123.212.145:90",
            "http://192.123.212.145:90/?ns=valid-namespace"),
        new CustomTestCase("https://valid-namespace.firebaseio.com", "[::1]:90",
            "http://[::1]:90/?ns=valid-namespace"),
        new CustomTestCase("https://firebaseio.com?ns=valid-namespace", "localhost:90",
            "http://localhost:90/?ns=valid-namespace"),
        new CustomTestCase(null, "localhost:90", "http://localhost:90/?ns=default"),
        new CustomTestCase("", "localhost:90", "http://localhost:90/?ns=default")
    );

    for (CustomTestCase tc : testCases) {
      TestUtils.setEnvironmentVariables(
          ImmutableMap.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR,
              Strings.nullToEmpty(tc.envVariableUrl)));
      assertEquals(tc.expectedEmulatorUrl, EmulatorHelper.getEmulatorUrl(tc.suppliedDbUrl));
      TestUtils.unsetEnvironmentVariables(
          ImmutableSet.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR));
    }
  }

  @Test
  public void testInvalidEmulatorUrlFromEnvVarThrows() {
    List<String> invalidEnvVars = ImmutableList.of(
        "http://localhost:8080",
        "http://localhost:8080?ns=test-ns",
        "localhost"
    );
    for (String invalidEnvVar : invalidEnvVars) {
      TestUtils.setEnvironmentVariables(
          ImmutableMap.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR,
              Strings.nullToEmpty(invalidEnvVar)));
      thrown.expect(IllegalArgumentException.class);
      EmulatorHelper.getEmulatorUrl("https://valid-namespace.firebaseio.com");
      TestUtils.unsetEnvironmentVariables(
          ImmutableSet.of(EmulatorHelper.FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR));
    }
  }
}


