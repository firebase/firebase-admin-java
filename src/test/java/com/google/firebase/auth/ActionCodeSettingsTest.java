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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

public class ActionCodeSettingsTest {

  @Test(expected = IllegalArgumentException.class)
  public void testNoUrl() {
    ActionCodeSettings.builder().build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMalformedUrl() {
    ActionCodeSettings.builder()
        .setUrl("not a url")
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyUrl() {
    ActionCodeSettings.builder()
        .setUrl("")
        .build();
  }

  @Test
  public void testUrlOnly() {
    ActionCodeSettings settings = ActionCodeSettings.builder()
        .setUrl("https://example.com")
        .build();
    Map<String, Object> expected = ImmutableMap.<String, Object>of(
        "continueUrl", "https://example.com", "canHandleCodeInApp", false);
    assertEquals(expected, settings.getProperties());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoAndroidPackageName() {
    ActionCodeSettings.builder()
        .setUrl("https://example.com")
        .setAndroidMinimumVersion("6.0")
        .setAndroidInstallApp(true)
        .build();
  }

  @Test
  public void testAllSettings() {
    ActionCodeSettings settings = ActionCodeSettings.builder()
        .setUrl("https://example.com")
        .setHandleCodeInApp(true)
        .setDynamicLinkDomain("myapp.page.link")
        .setIosBundleId("com.example.ios")
        .setAndroidPackageName("com.example.android")
        .setAndroidMinimumVersion("6.0")
        .setAndroidInstallApp(true)
        .build();
    Map<String, Object> expected = ImmutableMap.<String, Object>builder()
        .put("continueUrl", "https://example.com")
        .put("canHandleCodeInApp", true)
        .put("dynamicLinkDomain", "myapp.page.link")
        .put("iOSBundleId", "com.example.ios")
        .put("androidPackageName", "com.example.android")
        .put("androidMinimumVersion", "6.0")
        .put("androidInstallApp", true)
        .build();
    assertEquals(expected, settings.getProperties());
  }
}
