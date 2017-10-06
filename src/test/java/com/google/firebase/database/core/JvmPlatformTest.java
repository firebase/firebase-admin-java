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

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.ThreadManager;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.NonNull;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class JvmPlatformTest {

  @Test
  public void usesThreadManager() {
    final AtomicInteger count = new AtomicInteger(0);
    ThreadManager threadManager = new ThreadManager() {
      @Override
      protected ExecutorService getExecutor(@NonNull FirebaseApp app) {
        return Executors.newSingleThreadExecutor();
      }

      @Override
      protected void releaseExecutor(@NonNull FirebaseApp app,
          @NonNull ExecutorService executor) {
      }

      @Override
      protected ThreadFactory getThreadFactory() {
        count.incrementAndGet();
        return Executors.defaultThreadFactory();
      }
    };
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .setThreadManager(threadManager)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "threadManagerApp");

    try {
      assertEquals(0, count.get());
      Context cfg = new DatabaseConfig();
      cfg.firebaseApp = app;
      cfg.freeze();
      // EventTarget, RunLoop and AuthTokenProvider (token refresher)
      assertEquals(3, count.get());

      cfg.getConnectionContext();
      // ConnectionContext which gets passed to all low-level socket management code.
      assertEquals(4, count.get());
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
  }

  @Test
  public void userAgentHasCorrectParts() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "userAgentApp");

    try {
      Context cfg = new DatabaseConfig();
      cfg.firebaseApp = app;
      cfg.freeze();
      String userAgent = cfg.getUserAgent();
      String[] parts = userAgent.split("/");
      assertEquals(5, parts.length);
      assertEquals("Firebase", parts[0]); // Firebase
      assertEquals(Constants.WIRE_PROTOCOL_VERSION, parts[1]); // Wire protocol version
      assertEquals(FirebaseDatabase.getSdkVersion(), parts[2]); // SDK version
      assertEquals(System.getProperty("java.version", "Unknown"), parts[3]); // Java "OS" version
      assertEquals(Platform.DEVICE, parts[4]); // AdminJava
    } finally {
      TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    }
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
