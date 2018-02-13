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

package com.google.firebase.iid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.testing.IntegrationTestUtils;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseInstanceIdIT {

  @BeforeClass
  public static void setUpClass() throws Exception {
    IntegrationTestUtils.ensureDefaultApp();
  }

  @Test
  public void testDeleteNonExisting() throws Exception {
    FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance();
    try {
      // instance ids have to conform to /[cdef][A-Za-z0-9_-]{9}[AEIMQUYcgkosw048]/
      instanceId.deleteInstanceIdAsync("fictive-ID0").get();
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseInstanceIdException);
      assertEquals("Instance ID \"fictive-ID0\": Failed to find the instance ID.",
          e.getCause().getMessage());
    }
  }
}
