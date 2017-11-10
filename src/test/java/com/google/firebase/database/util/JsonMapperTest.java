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

package com.google.firebase.database.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.junit.Ignore;
import org.junit.Test;

public class JsonMapperTest {

  @Test
  public void testNull() throws IOException {
    assertEquals("null", JsonMapper.serializeJsonValue(null));
  }

  @Test
  public void testString() throws IOException {
    assertEquals("\"foo\"", JsonMapper.serializeJsonValue("foo"));
  }

  @Test
  public void testBoolean() throws IOException {
    assertEquals("true", JsonMapper.serializeJsonValue(true));
    assertEquals("false", JsonMapper.serializeJsonValue(false));
  }

  @Test
  public void testMap() throws IOException {
    assertEquals("{\"foo\":\"bar\"}", JsonMapper.serializeJsonValue(ImmutableMap.of("foo", "bar")));
  }

  @Test
  public void testList() throws IOException {
    assertEquals("[\"foo\",\"bar\"]", JsonMapper.serializeJsonValue(
        ImmutableList.of("foo", "bar")));
  }

  @Test
  public void testInvalidObject() {
    try {
      JsonMapper.serializeJsonValue(new Object());
      fail("No error thrown for invalid object");
    } catch (IOException expected) {
      // expected
      assertTrue(expected.getCause() instanceof JSONException);
    }
  }

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
  // TODO: Stop ignoring this test once JSON parsing has been fixed.
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

  @Test
  public void testParse() throws IOException {
    Map<String, Object> map = JsonMapper.parseJson("{\"foo\":\"bar\"}");
    assertEquals(ImmutableMap.of("foo", "bar"), map);

    Object result = JsonMapper.parseJsonValue("{\"foo\":\"bar\"}");
    assertEquals(ImmutableMap.of("foo", "bar"), result);

    try {
      JsonMapper.parseJson("{\"foo:bar}");
      fail("No error thrown for invalid json");
    } catch (IOException expected) {
      // expected
    }

    try {
      JsonMapper.parseJsonValue("{\"foo:bar}");
      fail("No error thrown for invalid json");
    } catch (IOException expected) {
      // expected
    }
  }
}
