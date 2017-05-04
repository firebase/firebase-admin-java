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

package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;

import com.google.firebase.database.FirebaseDatabase;
import org.junit.Assert;
import org.junit.Test;

public class JvmPlatformTest {

  @Test
  public void userAgentHasCorrectParts() {
    Context cfg = new DatabaseConfig();
    cfg.freeze();
    String userAgent = cfg.getUserAgent();
    String[] parts = userAgent.split("/");
    assertEquals(5, parts.length);
    assertEquals("Firebase", parts[0]); // Firebase
    assertEquals(Constants.WIRE_PROTOCOL_VERSION, parts[1]); // Wire protocol version
    assertEquals(FirebaseDatabase.getSdkVersion(), parts[2]); // SDK version
    assertEquals(System.getProperty("java.version", "Unknown"), parts[3]); // Java "OS" version
    assertEquals(Platform.DEVICE, parts[4]); // AdminJava
  }

  @Test
  public void sdkVersionIsWellFormed() {
    // Version number gets filled in during the release process.
    // Having a test case makes sure there are no mishaps.
    final String snapshot = "-SNAPSHOT";
    String sdkVersion = FirebaseDatabase.getSdkVersion();
    if (sdkVersion.endsWith(snapshot)) {
      sdkVersion = sdkVersion.substring(0, sdkVersion.length() - snapshot.length());
    }
    String[] segments = sdkVersion.split("\\.");
    Assert.assertEquals(3, segments.length);
    for (String segment : segments) {
      try {
        Integer.parseInt(segment);
      } catch (NumberFormatException e) {
        Assert.fail("Invalid version number string: " + sdkVersion);
      }
    }
  }
}
