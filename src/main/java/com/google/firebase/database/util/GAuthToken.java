package com.google.firebase.database.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a "gauth" token used by the Server SDK, which can contain a token and optionally a
 * auth payload.
 *
 * <p>HACK: Rather than plumb GAuthToken through our internals we serialize it to/from a string
 * (using JSON) and pass it through our normal plumbing that expects token to be a String.
 */
public class GAuthToken {

  // Normal tokens will be JWTs or possibly Firebase Secrets, neither of which will contain "|"
  // so this should be a safe prefix.
  private static final String TOKEN_PREFIX = "gauth|";
  private static final String AUTH_KEY = "auth";
  private static final String TOKEN_KEY = "token";
  private final String token;
  private final Map<String, Object> auth;

  public GAuthToken(String token, Map<String, Object> auth) {
    this.token = token;
    this.auth = auth;
  }

  public static GAuthToken tryParseFromString(String rawToken) {
    if (!rawToken.startsWith(TOKEN_PREFIX)) {
      return null;
    }

    String gauthToken = rawToken.substring(TOKEN_PREFIX.length());
    try {
      Map<String, Object> tokenMap = JsonMapper.parseJson(gauthToken);
      String token = (String) tokenMap.get(TOKEN_KEY);
      @SuppressWarnings("unchecked")
      Map<String, Object> auth = (Map<String, Object>) tokenMap.get(AUTH_KEY);
      return new GAuthToken(token, auth);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse gauth token", e);
    }
  }

  public String serializeToString() {
    Map<String, Object> tokenMap = new HashMap<>();
    tokenMap.put(TOKEN_KEY, token);
    tokenMap.put(AUTH_KEY, auth);
    try {
      String json = JsonMapper.serializeJson(tokenMap);
      return TOKEN_PREFIX + json;
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize gauth token", e);
    }
  }

  public String getToken() {
    return this.token;
  }

  public Map<String, Object> getAuth() {
    return this.auth;
  }
}
