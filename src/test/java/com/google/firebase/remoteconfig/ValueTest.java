/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValueTest {
  @Test
  public void testGetSourceReturnsValueSource() {
    Value value = new Value(ValueSource.STATIC);
    assertEquals(value.getSource(), ValueSource.STATIC);
  }

  @Test
  public void testAsStringReturnsValueAsString() {
    Value value = new Value(ValueSource.STATIC, "sample-string");
    assertEquals(value.asString(), "sample-string");
  }

  @Test
  public void testAsStringReturnsDefaultEmptyString() {
    Value value = new Value(ValueSource.STATIC);
    assertEquals(value.asString(), "");
  }

  @Test
  public void testAsLongReturnsDefaultValueForStaticSource() {
    Value value = new Value(ValueSource.STATIC);
    assertEquals(value.asLong(), 0L);
  }

  @Test
  public void testAsLongReturnsDefaultValueForInvalidSourceValue() {
    Value value = new Value(ValueSource.REMOTE, "sample-string");
    assertEquals(value.asLong(), 0L);
  }

  @Test
  public void testAsLongReturnsSourceValueAsLong() {
    Value value = new Value(ValueSource.REMOTE, "123");
    assertEquals(value.asLong(), 123L);
  }

  @Test
  public void testAsDoubleReturnsDefaultValueForStaticSource() {
    Value value = new Value(ValueSource.STATIC);
    assertEquals(value.asDouble(), 0, 0);
  }

  @Test
  public void testAsDoubleReturnsDefaultValueForInvalidSourceValue() {
    Value value = new Value(ValueSource.REMOTE, "sample-string");
    assertEquals(value.asDouble(), 0, 0);
  }

  @Test
  public void testAsDoubleReturnsSourceValueAsDouble() {
    Value value = new Value(ValueSource.REMOTE, "123.34");
    assertEquals(value.asDouble(), 123.34, 0);
  }
  
  @Test
  public void testAsBooleanReturnsDefaultValueForStaticSource() {
    Value value = new Value(ValueSource.STATIC);
    assertFalse(value.asBoolean());
  }

  @Test
  public void testAsBooleanReturnsDefaultValueForInvalidSourceValue() {
    Value value = new Value(ValueSource.REMOTE, "sample-string");
    assertFalse(value.asBoolean());
  }

  @Test
  public void testAsBooleanReturnsSourceValueAsBoolean() {
    Value value = new Value(ValueSource.REMOTE, "1");
    assertTrue(value.asBoolean());
  }

  @Test
  public void testAsBooleanReturnsSourceValueYesAsBoolean() {
    Value value = new Value(ValueSource.REMOTE, "YeS");
    assertTrue(value.asBoolean());
  }
}
