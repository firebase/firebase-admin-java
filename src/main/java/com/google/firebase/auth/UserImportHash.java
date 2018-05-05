/*
 * Copyright 2017 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Represents a hash algorithm and the related configuration parameters used to hash user
 * passwords. An instance of this class must be specified if importing any users with password
 * hashes (see {@link UserImportOptions.Builder#setHash(UserImportHash)}.
 *
 * <p>This is not expected to be extended in user code. Applications should use one of the provided
 * concrete implementations in the {@link com.google.firebase.auth.hash} package. See
 * <a href="https://firebase.google.com/docs/auth/admin/import-users">documentation</a> for more
 * details on available options.
 */
public abstract class UserImportHash {

  private final String name;

  protected UserImportHash(String name) {
    checkArgument(!Strings.isNullOrEmpty(name));
    this.name = name;
  }

  final Map<String, Object> getProperties() {
    return ImmutableMap.<String, Object>builder()
        .put("hashAlgorithm", name)
        .putAll(getOptions())
        .build();
  }

  protected abstract Map<String, Object> getOptions();
}
