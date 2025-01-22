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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.OneOfConditionResponse;

/**
 * Represents a condition that may be one of several types. Only the first defined field will be
 * processed.
 */
public class OneOfCondition {
  private OrCondition orCondition;
  private AndCondition andCondition;
  private PercentCondition percent;
  private CustomSignalCondition customSignal;
  private String trueValue;
  private String falseValue;

  /**
   * Creates a new {@link OneOfCondition} from API response.
   *
   * @param oneOfconditionResponse the conditions obtained from server call.
   */
  OneOfCondition(OneOfConditionResponse oneOfconditionResponse) {
    if (oneOfconditionResponse.getOrCondition() != null) {
      this.orCondition = new OrCondition(oneOfconditionResponse.getOrCondition());
    }
    if (oneOfconditionResponse.getAndCondition() != null) {
      this.andCondition = new AndCondition(oneOfconditionResponse.getAndCondition());
    }
    if (oneOfconditionResponse.getPercentCondition() != null) {
      this.percent = new PercentCondition(oneOfconditionResponse.getPercentCondition());
    }
    if (oneOfconditionResponse.getCustomSignalCondition() != null) {
      this.customSignal =
          new CustomSignalCondition(oneOfconditionResponse.getCustomSignalCondition());
    }
  }

  OneOfCondition() {
  }

/**
   * Gets {@link OrCondition} with conditions on which OR operation will be applied.
   *
   * @return list of conditions.
   */
  @Nullable
  public OrCondition getOrCondition() {
    return orCondition;
  }

  /**
   * Gets {@link AndCondition} with conditions on which AND operation will be applied.
   *
   * @return list of conditions.
   */
  @Nullable
  public AndCondition getAndCondition() {
    return andCondition;
  }

  /**
   * Returns true indicating the expression has evaluated to true.
   *
   * @return true.
   */
  @Nullable
  public String isTrue() {
    return trueValue;
  }

  /**
   * Returns false indicating the expression has evaluated to false.
   *
   * @return false.
   */
  @Nullable
  public String isFalse() {
    return falseValue;
  }

  /**
   * Gets {@link PercentCondition}.
   *
   * @return percent condition.
   */
  @Nullable
  public PercentCondition getPercent() {
    return percent;
  }

  /**
   * Gets {@link CustomSignalCondition}.
   *
   * @return custom condition.
   */
  @Nullable
  public CustomSignalCondition getCustomSignal() {
    return customSignal;
  }

  /**
   * Sets list of conditions on which OR operation will be applied.
   *
   * @param orCondition Makes this condition an OR condition.
   */
  public OneOfCondition setOrCondition(@NonNull OrCondition orCondition) {
    checkNotNull(orCondition, "`Or` condition cannot be set to null.");
    this.orCondition = orCondition;
    return this;
  }

  /**
   * Sets list of conditions on which AND operation will be applied.
   *
   * @param andCondition Makes this condition an AND condition.
   */
  public OneOfCondition setAndCondition(@NonNull AndCondition andCondition) {
    checkNotNull(andCondition, "`And` condition cannot be set to null.");
    this.andCondition = andCondition;
    return this;
  }

  /**
   * Sets {@link PercentCondition}.
   *
   * @param percent Makes this condition a percent condition.
   */
  public OneOfCondition setPercent(@NonNull PercentCondition percent) {
    checkNotNull(percent, "`Percent` condition cannot be set to null.");
    this.percent = percent;
    return this;
  }

  /**
   * Sets {@link CustomSignalCondition}.
   *
   * @param customSignal Makes this condition a custom signal condition.
   */
  public OneOfCondition setCustomSignal(@NonNull CustomSignalCondition customSignal) {
    checkNotNull(customSignal, "`Custom signal` condition cannot be set to null.");
    this.customSignal = customSignal;
    return this;
  }

  /** Sets evaluation value to true. */
  public OneOfCondition setTrue() {
    this.trueValue = "true";
    return this;
  }

  /** Sets evaluation value to false. */
  public OneOfCondition setFalse() {
    this.falseValue = "false";
    return this;
  }

  public OneOfConditionResponse toOneOfConditionResponse() {
    OneOfConditionResponse oneOfConditionResponse = new OneOfConditionResponse();
    if (this.andCondition != null) {
      oneOfConditionResponse.setAndCondition(this.andCondition.toAndConditionResponse());
    }
    if (this.orCondition != null) {
      oneOfConditionResponse.setOrCondition(this.orCondition.toOrConditionResponse());
    }
    if (this.percent != null) {
      oneOfConditionResponse.setPercentCondition(this.percent.toPercentConditionResponse());
    }
    if (this.customSignal != null) {
      oneOfConditionResponse.setCustomSignalCondition(this.customSignal.toCustomConditonResponse());
    }
    return oneOfConditionResponse;
  }
}
