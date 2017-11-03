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
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PairTest {

  @Test
  public void testPair() {
    Pair<String, Integer> pair1 = new Pair<>("foo", 99);
    assertEquals("foo", pair1.getFirst());
    assertEquals(99, (int) pair1.getSecond());
    assertEquals("Pair(foo,99)", pair1.toString());

    Pair<String, Integer> pair2 = new Pair<>("foo", 99);
    assertEquals("foo", pair2.getFirst());
    assertEquals(99, (int) pair2.getSecond());
    assertEquals("Pair(foo,99)", pair2.toString());

    Pair<String, Integer> pair3 = new Pair<>("foo", 100);
    assertEquals("foo", pair3.getFirst());
    assertEquals(100, (int) pair3.getSecond());
    assertEquals("Pair(foo,100)", pair3.toString());

    assertEquals(pair1, pair2);
    assertEquals(pair1.hashCode(), pair2.hashCode());

    assertNotEquals(pair1, pair3);
    assertNotEquals(pair1.hashCode(), pair3.hashCode());
  }

  @Test
  public void testPairWithNull() {
    Pair<String, String> pair1 = new Pair<>("foo", null);
    assertEquals("foo", pair1.getFirst());
    assertNull(pair1.getSecond());
    assertEquals("Pair(foo,null)", pair1.toString());

    Pair<String, String> pair2 = new Pair<>(null, "bar");
    assertNull(pair2.getFirst());
    assertEquals("bar", pair2.getSecond());
    assertEquals("Pair(null,bar)", pair2.toString());

    Pair<String, String> pair3 = new Pair<>(null, null);
    assertNull(pair3.getFirst());
    assertNull(pair3.getSecond());
    assertEquals("Pair(null,null)", pair3.toString());

    Pair<String, String> pair4 = new Pair<>("foo", null);
    assertEquals(pair1, pair4);
    assertEquals(pair1.hashCode(), pair4.hashCode());

    Pair<String, String> pair5 = new Pair<>(null, null);
    assertEquals(pair3, pair5);
    assertEquals(pair3.hashCode(), pair5.hashCode());
  }


}
