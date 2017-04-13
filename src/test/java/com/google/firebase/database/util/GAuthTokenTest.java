package com.google.firebase.database.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.database.MapBuilder;
import java.util.Map;
import org.junit.Test;

public class GAuthTokenTest {

  private static final Map<String, Object> exampleAuth =
      new MapBuilder().put("a", "a-val").put("b", 42).build();

  @Test
  public void construction() {
    GAuthToken token = new GAuthToken("token", exampleAuth);
    assertEquals("token", token.getToken());
    assertEquals(exampleAuth, token.getAuth());
  }

  @Test
  public void parseNonToken() {
    GAuthToken parsed = GAuthToken.tryParseFromString("notgauth|foo");
    assertNull(parsed);
  }

  @Test
  public void serializeDeserialize() {
    testRoundTrip(null, null);
    testRoundTrip("token", null);
    testRoundTrip(null, exampleAuth);
    testRoundTrip("token", exampleAuth);
  }

  private void testRoundTrip(String token, Map<String, Object> auth) {
    GAuthToken origToken = new GAuthToken(token, auth);
    GAuthToken restoredToken = GAuthToken.tryParseFromString(origToken.serializeToString());
    assertNotNull(restoredToken);
    assertEquals(token, restoredToken.getToken());
    assertEquals(auth, restoredToken.getAuth());
  }
}
