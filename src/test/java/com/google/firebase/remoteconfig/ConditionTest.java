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
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ConditionTest {

  @Test
  public void testEquality() {
    final Condition conditionOne = new Condition("ios", "device.os == 'ios'", TagColor.GREEN);
    final Condition conditionTwo = new Condition("ios", "device.os == 'ios'", TagColor.GREEN);
    final Condition conditionThree = new Condition("android", "device.os == 'android'");
    final Condition conditionFour = new Condition("android", "device.os == 'android'");
    final Condition conditionFive = new Condition("ios", "device.os == 'ios'", TagColor.BLUE);

    assertEquals(conditionOne, conditionTwo);
    assertEquals(conditionThree, conditionFour);
    assertNotEquals(conditionOne, conditionThree);
    assertNotEquals(conditionTwo, conditionFour);
    assertNotEquals(conditionOne, conditionFive);
  }
}
