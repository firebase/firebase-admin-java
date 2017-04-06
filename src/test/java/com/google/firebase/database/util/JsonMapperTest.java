package com.google.firebase.database.util;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JsonMapperTest {

  @Test
  public void canConvertLongs() throws IOException {
    List<Long> longs = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE);
    for (Long original : longs) {
      String jsonString = JsonMapper.serializeJsonValue(original);
      long converted = (Long) JsonMapper.parseJsonValue(jsonString);
      assertEquals((long) original, converted);
    }
  }

  @Test
  public void canConvertDoubles() throws IOException {
    List<Double> doubles = Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE, Double.MIN_NORMAL);
    for (Double original : doubles) {
      String jsonString = JsonMapper.serializeJsonValue(original);
      double converted = (Double) JsonMapper.parseJsonValue(jsonString);
      assertEquals(original, converted, 0);
    }
  }

  @Test
  @Ignore
  // TODO(depoll): Stop ignoring this test once JSON parsing has been fixed.
  public void canNest33LevelsDeep() throws IOException {
    Map<String, Object> root = new HashMap<>();
    Map<String, Object> currentMap = root;
    for (int i = 0; i < 33 - 1; i++) {
      Map<String, Object> newMap = new HashMap<>();
      currentMap.put("key", newMap);
      currentMap = newMap;
    }
    String jsonString = JsonMapper.serializeJsonValue(root);
    Object value = JsonMapper.parseJsonValue(jsonString);
    assertEquals(root, value);
  }
}
