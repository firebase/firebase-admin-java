/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.auth;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class UserImportOptions {

  private final UserImportHash hash;

  private UserImportOptions(Builder builder) {
    this.hash = builder.hash;
  }

  public static UserImportOptions withHash(UserImportHash hash) {
    return builder().setHash(hash).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  Map<String, Object> getProperties() {
    ImmutableMap.Builder<String, Object> properties = ImmutableMap.builder();
    if (hash != null) {
      properties.putAll(hash.getProperties());
    }
    return properties.build();
  }

  UserImportHash getHash() {
    return hash;
  }

  public static class Builder {

    private UserImportHash hash;

    private Builder() {}

    public Builder setHash(UserImportHash hash) {
      this.hash = hash;
      return this;
    }

    public UserImportOptions build() {
      return new UserImportOptions(this);
    }
  }
}
