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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.common.io.CharStreams;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class StorageClientIT {

  @Test
  public void testCloudStorageDefaultBucket() {
    StorageClient storage = StorageClient.getInstance(IntegrationTestUtils.ensureDefaultApp());
    testBucket(storage.bucket());
  }

  @Test
  public void testCloudStorageCustomBucket() {
    StorageClient storage = StorageClient.getInstance(IntegrationTestUtils.ensureDefaultApp());
    testBucket(storage.bucket(IntegrationTestUtils.getStorageBucket()));
  }

  @Test
  public void testCloudStorageNonExistingBucket() {
    StorageClient storage = StorageClient.getInstance(IntegrationTestUtils.ensureDefaultApp());
    try {
      storage.bucket("non-existing");
      fail("No error thrown for non-existing bucket");
    } catch (IllegalArgumentException expected) {
      // ignore
    }
  }

  @Test
  public void testCloudStorageSignUrl() throws IOException {
    StorageClient storage = StorageClient.getInstance(IntegrationTestUtils.ensureDefaultApp());
    Bucket bucket = storage.bucket();
    Blob blob = createTextBlob(bucket, "Signed URL Test");
    URL url = blob.signUrl(3600, TimeUnit.SECONDS);
    try (InputStream in = url.openStream()) {
      String result = CharStreams.toString(new InputStreamReader(in));
      assertEquals("Signed URL Test", result);
    } finally {
      blob.delete();
    }
  }

  private void testBucket(Bucket bucket) {
    assertEquals(IntegrationTestUtils.getStorageBucket(), bucket.getName());
    String fileName = createTextBlob(bucket, "Hello World").getName();

    Blob blob = bucket.get(fileName);
    byte[] content = blob.getContent();
    assertEquals("Hello World", new String(content));

    assertTrue(blob.delete());
    assertNull(bucket.get(fileName));
  }

  private Blob createTextBlob(Bucket bucket, String contents) {
    String fileName = "data_" + System.currentTimeMillis() + ".txt";
    return bucket.create(fileName, contents.getBytes(), "text/plain");
  }

}
