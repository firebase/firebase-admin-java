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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

public class StorageClientTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testInvalidConfiguration() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .build());
    try {
      StorageClient.getInstance(app).bucket();
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }
  }

  @Test
  public void testInvalidBucketName() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());
    try {
      StorageClient.getInstance(app).bucket(null);
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }

    try {
      StorageClient.getInstance(app).bucket("");
      fail("No error thrown for invalid configuration");
    } catch (IllegalArgumentException expected) {
      // ignore
    }
  }

  @Test
  public void testAppDelete() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());

    assertNotNull(StorageClient.getInstance());
    assertNotNull(StorageClient.getInstance(app));
    app.delete();
    try {
      StorageClient.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }

  @Test
  public void testNonExistingBucket() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());
    Storage mockStorage = Mockito.mock(Storage.class);
    StorageClient client = new StorageClient(app, mockStorage);
    try {
      client.bucket();
      fail("No error thrown for non existing bucket");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    try {
      client.bucket("mock-bucket-name");
      fail("No error thrown for non existing bucket");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testBucket() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("mock-bucket-name")
        .build());
    Storage mockStorage = Mockito.mock(Storage.class);
    Bucket mockBucket = Mockito.mock(Bucket.class);
    Mockito.when(mockStorage.get("mock-bucket-name")).thenReturn(mockBucket);
    StorageClient client = new StorageClient(app, mockStorage);
    assertSame(mockBucket, client.bucket());
    assertSame(mockBucket, client.bucket("mock-bucket-name"));
  }
}
