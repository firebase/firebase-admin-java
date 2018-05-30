/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.auth;

import static org.junit.Assert.assertEquals;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class UserProviderTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  @Test
  public void testAllProperties() throws IOException {
    UserProvider provider = UserProvider.builder()
        .setUid("testuid")
        .setProviderId("google.com")
        .setEmail("test@example.com")
        .setDisplayName("Test User")
        .setPhotoUrl("https://test.com/user.png")
        .build();
    String json = JSON_FACTORY.toString(provider);
    Map<String, Object> parsed = new HashMap<>();
    JSON_FACTORY.createJsonParser(json).parse(parsed);
    Map<String, Object> expected = ImmutableMap.<String, Object>of(
        "rawId", "testuid",
        "providerId", "google.com",
        "email", "test@example.com",
        "displayName", "Test User",
        "photoUrl", "https://test.com/user.png"
    );
    assertEquals(expected, parsed);
  }

  @Test
  public void testRequiredProperties() throws IOException {
    UserProvider provider = UserProvider.builder()
        .setUid("testuid")
        .setProviderId("google.com")
        .build();
    String json = JSON_FACTORY.toString(provider);
    Map<String, Object> parsed = new HashMap<>();
    JSON_FACTORY.createJsonParser(json).parse(parsed);
    Map<String, Object> expected = ImmutableMap.<String, Object>of(
        "rawId", "testuid",
        "providerId", "google.com"
    );
    assertEquals(expected, parsed);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoUid() {
    UserProvider.builder()
        .setProviderId("google.com")
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoProviderId() {
    UserProvider.builder()
        .setUid("testuid")
        .build();
  }
}
