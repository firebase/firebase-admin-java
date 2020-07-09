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

package com.google.firebase.auth.internal;

import com.google.firebase.auth.ProviderConfig;
import java.util.List;

/**
 * Interface for config list response messages sent by Google identity toolkit service.
 */
public interface ListProviderConfigsResponse<T extends ProviderConfig> {

  public List<T> getProviderConfigs();

  public boolean hasProviderConfigs();

  public String getPageToken();
}
