package com.google.firebase.database;

/**
 * User: greg Date: 5/23/13 Time: 5:32 PM
 */
public class TestConstants {

  public static final String TEST_NAMESPACE = "http://tests.fblocal.com:9000";
  public static final String TEST_ALT_NAMESPACE = "http://tests2.fblocal.com:9000";
  public static final String TEST_REPO = "tests";
  public static final String TEST_SERVER = "fblocal.com:9000";
  public static final String TEST_AUTH_SERVER = "http://fblocal.com:12000";
  public static final String TEST_DOMAIN = "fblocal.com";
  public static final String DEFAULT_RULES_STRING =
      "{\n    \"rules\": {\n        \".read\": true,\n        \".write\": true\n    }\n}";

  public static final long TEST_TIMEOUT = 7 * 1000;
}
