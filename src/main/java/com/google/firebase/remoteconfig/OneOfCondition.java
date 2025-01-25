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

class OneOfCondition {
  private OrCondition orCondition;
  private AndCondition andCondition;
  private PercentCondition percent;
  private CustomSignalCondition customSignal;
  private String trueValue;
  private String falseValue;

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

  OneOfCondition() {}

  @Nullable
  OrCondition getOrCondition() {
    return orCondition;
  }

  @Nullable
  AndCondition getAndCondition() {
    return andCondition;
  }

  @Nullable
  String isTrue() {
    return trueValue;
  }

  @Nullable
  String isFalse() {
    return falseValue;
  }

  @Nullable
  PercentCondition getPercent() {
    return percent;
  }

  @Nullable
  CustomSignalCondition getCustomSignal() {
    return customSignal;
  }

  OneOfCondition setOrCondition(@NonNull OrCondition orCondition) {
    checkNotNull(orCondition, "`Or` condition cannot be set to null.");
    this.orCondition = orCondition;
    return this;
  }

  OneOfCondition setAndCondition(@NonNull AndCondition andCondition) {
    checkNotNull(andCondition, "`And` condition cannot be set to null.");
    this.andCondition = andCondition;
    return this;
  }

  OneOfCondition setPercent(@NonNull PercentCondition percent) {
    checkNotNull(percent, "`Percent` condition cannot be set to null.");
    this.percent = percent;
    return this;
  }

  OneOfCondition setCustomSignal(@NonNull CustomSignalCondition customSignal) {
    checkNotNull(customSignal, "`Custom signal` condition cannot be set to null.");
    this.customSignal = customSignal;
    return this;
  }

  OneOfCondition setTrue() {
    this.trueValue = "true";
    return this;
  }

  OneOfCondition setFalse() {
    this.falseValue = "false";
    return this;
  }

  OneOfConditionResponse toOneOfConditionResponse() {
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
