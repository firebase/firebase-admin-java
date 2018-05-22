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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.UploadAccountResponse;
import com.google.firebase.internal.NonNull;
import java.util.List;

/**
 * Represents the result of the {@link FirebaseAuth#importUsersAsync(List, UserImportOptions)} API.
 */
public final class UserImportResult {

  private final int users;
  private final ImmutableList<ErrorInfo> errors;

  UserImportResult(int users, UploadAccountResponse response) {
    ImmutableList.Builder<ErrorInfo> errorsBuilder = ImmutableList.builder();
    List<UploadAccountResponse.ErrorInfo> errors = response.getErrors();
    if (errors != null) {
      checkArgument(users >= errors.size());
      for (UploadAccountResponse.ErrorInfo error : errors) {
        errorsBuilder.add(new ErrorInfo(error.getIndex(), error.getMessage()));
      }
    }
    this.users = users;
    this.errors = errorsBuilder.build();
  }

  /**
   * Returns the number of users that were imported successfully.
   *
   * @return number of users successfully imported (possibly zero).
   */
  public int getSuccessCount() {
    return users - errors.size();
  }

  /**
   * Returns the number of users that failed to be imported.
   *
   * @return number of users that resulted in import failures (possibly zero).
   */
  public int getFailureCount() {
    return errors.size();
  }

  /**
   * A list of {@link ErrorInfo} instances describing the errors that were encountered during
   * the import. Length of this list is equal to the return value of {@link #getFailureCount()}.
   *
   * @return A non-null list (possibly empty).
   */
  @NonNull public List<ErrorInfo> getErrors() {
    return errors;
  }
}
