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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to convert from/to JSON strings. TODO: This class should ideally not live in
 * firebase-database-connection, but it's required by both firebase-database and
 * firebase-database-connection, so leave it here for now.
 */
public class JsonMapper {

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  public static String serializeJson(Object object) throws IOException {
    try {
      return GSON.toJson(object);
    } catch (JsonSyntaxException e) {
      throw new IOException(e);
    }
  }

  public static Map<String, Object> parseJson(String json) throws IOException {
    try {
      JsonReader jsonReader = new JsonReader(new StringReader(json));
      return unwrapJsonObject(jsonReader);
    } catch (IllegalStateException | JsonSyntaxException e) {
      throw new IOException(e);
    }
  }

  public static Object parseJsonValue(String json) throws IOException {
    try {
      JsonReader jsonReader = new JsonReader(new StringReader(json));
      jsonReader.setLenient(true);
      return unwrapJson(jsonReader);
    } catch (IllegalStateException | JsonSyntaxException e) {
      throw new IOException(e);
    }
  }

  private static Map<String, Object> unwrapJsonObject(JsonReader jsonReader) throws IOException {
    Map<String, Object> map = new HashMap<>();
    jsonReader.beginObject();
    while (jsonReader.peek() != JsonToken.END_OBJECT) {
      String key = jsonReader.nextName();
      map.put(key, unwrapJson(jsonReader));
    }
    jsonReader.endObject();
    return map;
  }

  private static List<Object> unwrapJsonArray(JsonReader jsonReader) throws IOException {
    List<Object> list = new ArrayList<>();
    jsonReader.beginArray();
    while (jsonReader.peek() != JsonToken.END_ARRAY) {
      list.add(unwrapJson(jsonReader));
    }
    jsonReader.endArray();
    return list;
  }

  private static Object unwrapJson(JsonReader jsonReader) throws IOException {
    switch (jsonReader.peek()) {
      case BEGIN_ARRAY:
        return unwrapJsonArray(jsonReader);
      case BEGIN_OBJECT:
        return unwrapJsonObject(jsonReader);
      case STRING:
        return jsonReader.nextString();
      case NUMBER:
        String value = jsonReader.nextString();
        if (value.matches("-?\\d+")) {
          long longValue = Long.parseLong(value);
          if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
            return (int) longValue;
          }
          return Long.valueOf(value);
        }
        return Double.parseDouble(value);
      case BOOLEAN:
        return jsonReader.nextBoolean();
      case NULL:
        jsonReader.nextNull();
        return null;
      default:
        throw new IllegalStateException("unknown type " + jsonReader.peek());
    }
  }

}
