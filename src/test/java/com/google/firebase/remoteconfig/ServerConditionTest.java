/*
 * Copyright 2025 Google LLC
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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.CustomSignalConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OneOfConditionResponse;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.ServerConditionResponse;
import java.util.ArrayList;
import org.junit.Test;

/** Tests 
* for {@link ServerCondition}. 
* */
public class ServerConditionTest {

  @Test
  public void testConstructor() {
    OneOfCondition conditions =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(
                                                        ImmutableList.of("100"))))))))));
    ServerCondition serverCondition = new ServerCondition("ios_en_1", conditions);

    assertEquals("ios_en_1", serverCondition.getName());
    assertEquals(conditions, serverCondition.getCondition());
  }

  @Test
  public void testConstructorWithResponse() {
    CustomSignalConditionResponse customResponse =
        new CustomSignalConditionResponse()
            .setKey("test_key")
            .setOperator("NUMERIC_EQUAL")
            .setTargetValues(ImmutableList.of("1"));
    OneOfConditionResponse conditionResponse =
        new OneOfConditionResponse().setCustomSignalCondition(customResponse);
    ServerConditionResponse response =
        new ServerConditionResponse().setName("ios_en_2").setServerCondition(conditionResponse);
    ServerCondition serverCondition = new ServerCondition(response);

    assertEquals("ios_en_2", serverCondition.getName());
    assertEquals("test_key", serverCondition.getCondition().getCustomSignal().getCustomSignalKey());
  }

  @Test
  public void testIllegalConstructor() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new ServerCondition(null, null));

    assertEquals("condition name must not be null or empty", error.getMessage());
  }

  @Test
  public void testConstructorWithNullServerConditionResponse() {
    assertThrows(NullPointerException.class, () -> new ServerCondition(null));
  }

  @Test
  public void testSetNullName() {
    OneOfCondition conditions =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(
                                                        ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> condition.setName(null));

    assertEquals("condition name must not be null or empty", error.getMessage());
  }

  @Test
  public void testSetEmptyName() {
    OneOfCondition conditions =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(
                                                        ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> condition.setName(""));

    assertEquals("condition name must not be null or empty", error.getMessage());
  }

  @Test
  public void testSetNullServerCondition() {
    OneOfCondition conditions =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(
                                                        ImmutableList.of("100"))))))))));
    ServerCondition condition = new ServerCondition("ios", conditions);
    
    assertThrows(NullPointerException.class, () -> condition.setServerCondition(null));
  }

  @Test
  public void testEquality() {
    OneOfCondition conditionOne =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(
                                                        ImmutableList.of("100"))))))))));
    OneOfCondition conditionTwo =
        new OneOfCondition()
            .setOrCondition(
                new OrCondition(
                    ImmutableList.of(
                        new OneOfCondition()
                            .setAndCondition(
                                new AndCondition(
                                    ImmutableList.of(
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                    "users",
                                                    CustomSignalOperator.NUMERIC_LESS_THAN,
                                                    new ArrayList<>(ImmutableList.of("100")))),
                                        new OneOfCondition()
                                            .setCustomSignal(
                                                new CustomSignalCondition(
                                                            "users",
                                                    CustomSignalOperator
                                                        .NUMERIC_GREATER_THAN,
                                                    new ArrayList<>(ImmutableList.of("20")))),
                                        new OneOfCondition()
                                            .setPercent(
                                                new PercentCondition(
                                                    new MicroPercentRange(25000000, 100000000),
                                                    PercentConditionOperator.BETWEEN,
                                                    "cla24qoibb61"))))))));

    final ServerCondition serverConditionOne = new ServerCondition("ios", conditionOne);
    final ServerCondition serverConditionTwo = new ServerCondition("ios", conditionOne);
    final ServerCondition serverConditionThree = new ServerCondition("android", conditionTwo);
    final ServerCondition serverConditionFour = new ServerCondition("android", conditionTwo);

    assertEquals(serverConditionOne, serverConditionTwo);
    assertEquals(serverConditionThree, serverConditionFour);
  }
}
