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

package com.google.firebase.database;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;

/**
 * This error is thrown when the Firebase Database library is unable to operate on the input it has
 * been given.
 */
public class DatabaseException extends FirebaseException {

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param message A human readable description of the error
   */
  public DatabaseException(ErrorCode code, String message) {
    super(code, message, null, null);
  }
}
