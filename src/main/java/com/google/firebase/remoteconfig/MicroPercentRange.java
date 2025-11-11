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

import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.ServerTemplateResponse.MicroPercentRangeResponse;

class MicroPercentRange {
  private final int microPercentLowerBound;
  private final int microPercentUpperBound;

  public MicroPercentRange(@Nullable Integer microPercentLowerBound,
      @Nullable Integer microPercentUpperBound) {
    this.microPercentLowerBound = microPercentLowerBound != null ? microPercentLowerBound : 0;
    this.microPercentUpperBound = microPercentUpperBound != null ? microPercentUpperBound : 0;
  }

  int getMicroPercentLowerBound() {
    return microPercentLowerBound;
  }

  int getMicroPercentUpperBound() {
    return microPercentUpperBound;
  }

  MicroPercentRangeResponse toMicroPercentRangeResponse() {
    MicroPercentRangeResponse microPercentRangeResponse = new MicroPercentRangeResponse();
    microPercentRangeResponse.setMicroPercentLowerBound(this.microPercentLowerBound);
    microPercentRangeResponse.setMicroPercentUpperBound(this.microPercentUpperBound);
    return microPercentRangeResponse;
  }
}
