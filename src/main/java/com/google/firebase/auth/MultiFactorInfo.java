/*
 * Copyright 2022 Google Inc.
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

import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.internal.Nullable;

/**
 * Interface representing the common properties of a user-enrolled second factor.
 */
public abstract class MultiFactorInfo {

  /**
   * The ID of the enrolled second factor. This ID is unique to the user.
   */
  private final String uid;

  /**
   * The optional display name of the enrolled second factor.
   */
  private final String displayName;

  /**
   * The type identifier of the second factor. For SMS second factors, this is `phone`.
   */
  private final String factorId;

  /**
   * The optional date the second factor was enrolled, formatted as a UTC string.
   */
  private final String enrollmentTime;

  MultiFactorInfo(GetAccountInfoResponse.MultiFactorInfo response) {
    this.uid = response.getMfaEnrollmentId();
    this.displayName = response.getDisplayName();
    this.factorId = response.getFactorId();
    this.enrollmentTime = response.getEnrollmentTime();
  }

  public String getUid() {
    return uid;
  }

  @Nullable
  public String getDisplayName() {
    return displayName;
  }

  public String getFactorId() {
    return factorId;
  }

  @Nullable
  public String getEnrollmentTime() {
    return enrollmentTime;
  }
}
