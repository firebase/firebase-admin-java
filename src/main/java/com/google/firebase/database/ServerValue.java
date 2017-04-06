package com.google.firebase.database;

// Server values

import com.google.firebase.database.core.ServerValues;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Contains placeholder values to use when writing data to the Firebase Database. */
public class ServerValue {

  /**
   * A placeholder value for auto-populating the current timestamp (time since the Unix epoch, in
   * milliseconds) by the Firebase Database servers.
   */
  public static final Map<String, String> TIMESTAMP = createServerValuePlaceholder("timestamp");

  private static Map<String, String> createServerValuePlaceholder(String key) {
    Map<String, String> result = new HashMap<>();
    result.put(ServerValues.NAME_SUBKEY_SERVERVALUE, key);
    return Collections.unmodifiableMap(result);
  }
}
