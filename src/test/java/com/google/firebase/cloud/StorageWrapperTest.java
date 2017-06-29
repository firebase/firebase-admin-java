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

package com.google.firebase.cloud;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;

public class StorageWrapperTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testInvalidConfiguration() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .build());
    try {
      StorageWrapper.getInstance(app).getBucket();
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }
  }

  @Test
  public void testInvalidBucketName() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());
    try {
      StorageWrapper.getInstance(app).getBucket(null);
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }

    try {
      StorageWrapper.getInstance(app).getBucket("");
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }
  }

  @Test
  public void testAppDelete() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());

    assertNotNull(StorageWrapper.getInstance(app));
    app.delete();
    try {
      StorageWrapper.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }
}
