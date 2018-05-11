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

package com.google.firebase.auth.hash;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.UserImportHash;
import java.util.Map;

/**
 * Represents the Bcrypt password hashing algorithm. Can be used as an instance of
 * {@link com.google.firebase.auth.UserImportHash} when importing users.
 */
public class Bcrypt extends UserImportHash {

  private Bcrypt() {
    super("BCRYPT");
  }

  public static Bcrypt getInstance() {
    return new Bcrypt();
  }

  @Override
  protected Map<String, Object> getOptions() {
    return ImmutableMap.of();
  }
}
