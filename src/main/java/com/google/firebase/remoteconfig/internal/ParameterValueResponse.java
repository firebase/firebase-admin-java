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

package com.google.firebase.remoteconfig.internal;

import com.google.api.client.util.Key;
import com.google.firebase.remoteconfig.ExplicitParameterValue;
import com.google.firebase.remoteconfig.InAppDefaultValue;
import com.google.firebase.remoteconfig.RemoteConfigParameterValue;

public final class ParameterValueResponse {

  @Key("value")
  private String value;

  @Key("useInAppDefault")
  private Boolean inAppDefaultValue;

  public ParameterValueResponse() {
  }

  private ParameterValueResponse(String value, Boolean inAppDefaultValue) {
    this.value = value;
    this.inAppDefaultValue = inAppDefaultValue;
  }

  public static ParameterValueResponse ofValue(String value) {
    return new ParameterValueResponse(value, null);
  }

  public static ParameterValueResponse ofInAppDefaultValue() {
    return new ParameterValueResponse(null, true);
  }

  public RemoteConfigParameterValue toValue() {
    if (this.inAppDefaultValue) {
      return InAppDefaultValue.getInstance();
    }
    return ExplicitParameterValue.of(this.value);
  }
}
