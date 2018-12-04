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
        "url", "https://example.com", "handleCodeInApp", false);
    assertEquals(expected, settings.getProperties());
  }

  @Test
  public void testAllSettings() {
    ActionCodeSettings settings = ActionCodeSettings.builder()
        .setUrl("https://example.com")
        .setHandleCodeInApp(true)
        .setDynamicLinkDomain("myapp.page.link")
        .setIosActionCodeSettings(new IosActionCodeSettings("com.example.ios"))
        .setAndroidActionCodeSettings(AndroidActionCodeSettings.builder()
            .setPackageName("com.example.android")
            .setMinimumVersion("6.0")
            .setInstallApp(true)
            .build())
        .build();
    Map<String, Object> expected = ImmutableMap.<String, Object>of(
        "url", "https://example.com",
        "handleCodeInApp", true,
        "dynamicLinkDomain", "myapp.page.link",
        "iOS", ImmutableMap.of("bundleId", "com.example.ios"),
        "android", ImmutableMap.<String, Object>of(
            "packageName", "com.example.android",
            "minimumVersion", "6.0",
            "installApp", true)
    );
    assertEquals(expected, settings.getProperties());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullBundleIdIosActionSettings() {
    new IosActionCodeSettings(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyBundleIdIosActionSettings() {
    new IosActionCodeSettings("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoPackageNameAndroidActionSettings() {
    AndroidActionCodeSettings.builder().build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyPackageNameAndroidActionSettings() {
    AndroidActionCodeSettings.builder()
        .setPackageName("")
        .build();
  }
}
