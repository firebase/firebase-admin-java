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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;

/** Base class for all Firebase exceptions. */
public class FirebaseException extends Exception {

  // TODO(b/27677218): Exceptions should have non-empty messages.
  @Deprecated
  protected FirebaseException() {}

  public FirebaseException(@NonNull String detailMessage) {
    super(detailMessage);
    checkArgument(!Strings.isNullOrEmpty(detailMessage), "Detail message must not be empty");
  }

  public FirebaseException(@NonNull String detailMessage, Throwable cause) {
    super(detailMessage, cause);
    checkArgument(!Strings.isNullOrEmpty(detailMessage), "Detail message must not be empty");
  }
}
