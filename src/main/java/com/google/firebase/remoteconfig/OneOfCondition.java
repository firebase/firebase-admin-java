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
      this.customSignal = new CustomSignalCondition(
        oneOfconditionResponse.getCustomSignalCondition());
    }
  }

  public OneOfCondition() {}

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
  public void setOrCondition(@NonNull OrCondition orCondition) {
    this.orCondition = orCondition;
  }

  /**
   * Sets list of conditions on which AND operation will be applied.
   *
   * @param andCondition Makes this condition an AND condition.
   */
  public void setAndCondition(@NonNull AndCondition andCondition) {
    this.andCondition = andCondition;
  }

  /**
   * Sets {@link PercentCondition}.
   *
   * @param percent Makes this condition a percent condition.
   */
  public void setPercent(@NonNull PercentCondition percent) {
    this.percent = percent;
  }

  /**
   * Sets {@link CustomSignalCondition}.
   *
   * @param customSignal Makes this condition a custom signal condition.
   */
  public void setCustomSignal(@NonNull CustomSignalCondition customSignal) {
    this.customSignal = customSignal;
  }

  /** Sets evaluation value to true. */
  public void setTrue() {
    this.trueValue = "true";
  }

  /** Sets evaluation value to false. */
  public void setFalse() {
    this.falseValue = "false";
  }
}
