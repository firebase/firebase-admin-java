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

package com.google.firebase.database.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class ConnectionUtilsTest {

  @Test
  public void testStringToPath() {
    assertEquals(ImmutableList.of(), ConnectionUtils.stringToPath(""));
    assertEquals(ImmutableList.of(), ConnectionUtils.stringToPath("/"));
    assertEquals(ImmutableList.of("foo"), ConnectionUtils.stringToPath("/foo"));
    assertEquals(ImmutableList.of("foo", "bar"), ConnectionUtils.stringToPath("/foo/bar"));
    assertEquals(ImmutableList.of("foo", "bar"), ConnectionUtils.stringToPath("/foo//bar"));
  }

  @Test
  public void testPathToString() {
    assertEquals("/", ConnectionUtils.pathToString(ImmutableList.<String>of()));
    assertEquals("foo", ConnectionUtils.pathToString(ImmutableList.of("foo")));
    assertEquals("foo/bar", ConnectionUtils.pathToString(ImmutableList.of("foo", "bar")));
  }

  @Test
  public void testLongFromObject() {
    assertEquals(new Long(10), ConnectionUtils.longFromObject(10L));
    assertEquals(new Long(10), ConnectionUtils.longFromObject(new Long(10)));
    assertEquals(new Long(10), ConnectionUtils.longFromObject(10));
    assertNull(ConnectionUtils.longFromObject("foo"));
  }

  @Test
  public void testHardAssert() {
    ConnectionUtils.hardAssert(true);
    try {
      ConnectionUtils.hardAssert(false);
      fail("No error thrown for failed hardAssert");
    } catch (AssertionError e) {
      assertEquals("hardAssert failed: ", e.getMessage());
    }
  }

  @Test
  public void testHardAssertWithMessage() {
    ConnectionUtils.hardAssert(true, "foo %s", "bar");
    try {
      ConnectionUtils.hardAssert(false,"foo %s", "bar");
      fail("No error thrown for failed hardAssert");
    } catch (AssertionError e) {
      assertEquals("hardAssert failed: foo bar", e.getMessage());
    }
  }

}
