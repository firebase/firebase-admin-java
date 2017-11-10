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

package com.google.firebase.database.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PushIdGeneratorTest {

  private static final String ALLOWED_CHARS =
      "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  @Test
  public void testGenerateDuplicateTime() {
    String id1 = PushIdGenerator.generatePushChildName(100L);
    checkId(id1);

    String id2 = PushIdGenerator.generatePushChildName(100L);
    checkId(id2);

    assertNotEquals(id1, id2);
  }

  @Test
  public void testGenerate() {
    String id1 = PushIdGenerator.generatePushChildName(100L);
    checkId(id1);

    String id2 = PushIdGenerator.generatePushChildName(101L);
    checkId(id2);

    assertNotEquals(id1, id2);
  }

  @Test
  public void testCharRecycle() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      // Characters will recycle after 64
      ids.add(PushIdGenerator.generatePushChildName(i));
    }

    for (int i = 0; i < 100; i++) {
      String id = ids.get(i);
      checkId(id);
      for (int j = 0; j < 100; j++) {
        if (i != j) {
          assertNotEquals(id, ids.get(j));
        }
      }
    }
  }

  private void checkId(String id) {
    assertTrue(!Strings.isNullOrEmpty(id));
    assertEquals(20, id.length());
    for (int i = 0; i < 20; i++) {
      assertTrue(ALLOWED_CHARS.contains(String.valueOf(id.charAt(i))));
    }
  }

}
