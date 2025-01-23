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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.CustomSignalConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OneOfConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.ServerConditionResponse;

public class ServerConditionTest {

  @Test
  public void testConstructor() {
    OneOfCondition conditions =  new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100"))))))))));
    ServerCondition serverCondition = new ServerCondition("ios_en_1",conditions);

    assertEquals("ios_en_1", serverCondition.getName());
    assertEquals(conditions, serverCondition.getCondition());
  }

  @Test
  public void testConstructorWithResponse() {
    CustomSignalConditionResponse customResponse = new CustomSignalConditionResponse().setKey("test_key");
    OneOfConditionResponse conditionResponse = new OneOfConditionResponse().setCustomSignalCondition(customResponse);
    ServerConditionResponse response = new ServerConditionResponse().setName("ios_en_2").setServerCondtion(conditionResponse);
    ServerCondition serverCondition = new ServerCondition(response);

    assertEquals("ios_en_2", serverCondition.getName());
    assertEquals("test_key", serverCondition.getCondition().getCustomSignal().getCustomSignalKey());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalConstructor() {
    new ServerCondition(null, null);
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullServerConditionResponse() {
    new ServerCondition(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetNullName() {
    OneOfCondition conditions =  new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    condition.setName(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetEmptyName() {
    OneOfCondition conditions =  new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    condition.setName("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetNullServerCondition() {
    OneOfCondition conditions =  new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    condition.setServerCondition(null);
  }

  @Test
  public void testEquality() {
    OneOfCondition conditionOne = new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100"))))))))));
    OneOfCondition conditionTwo = new OneOfCondition().setOrCondition(new OrCondition(ImmutableList.of(new OneOfCondition().setAndCondition(new AndCondition(ImmutableList.of(new OneOfCondition().setCustomSignal(new CustomSignalCondition("users",  CustomSignalOperator.NUMERIC_LESS_THAN, new ArrayList<>(ImmutableList.of("100")))), new OneOfCondition().setPercent(new PercentCondition(new MicroPercentRange(25000000, 100000000), PercentConditionOperator.BETWEEN, "cla24qoibb61"))))))));

    final ServerCondition serverConditionOne = new ServerCondition("ios", conditionOne);
    final ServerCondition serverConditionTwo = new ServerCondition("ios", conditionOne);
    final ServerCondition serverConditionThree = new ServerCondition("android", conditionTwo);
    final ServerCondition serverConditionFour = new ServerCondition("android", conditionTwo);

    assertEquals(serverConditionOne, serverConditionTwo);
    assertEquals(serverConditionThree, serverConditionFour);
  }
}
