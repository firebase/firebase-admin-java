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
import com.google.firebase.testing.IntegrationTestUtils;
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

  private void testBucket(Bucket bucket) {
    assertEquals(IntegrationTestUtils.getStorageBucket(), bucket.getName());

    String fileName = "data_" + System.currentTimeMillis() + ".txt";
    bucket.create(fileName, "Hello World".getBytes(), "text/plain");

    Blob blob = bucket.get(fileName);
    byte[] content = blob.getContent();
    assertEquals("Hello World", new String(content));

    assertTrue(blob.delete());
    assertNull(bucket.get(fileName));
  }

}
