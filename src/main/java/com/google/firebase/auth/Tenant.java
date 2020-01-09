/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

import com.google.auto.value.AutoValue;

/**
 * Contains metadata associated with a Firebase tenant.
 * 
 * <p>Instances of this class are immutable and thread safe.
 */
@AutoValue
public abstract class Tenant {

  /**
   * Returns the ID of this tenant.
   *
   * @return a non-empty tenant ID string.
   */
  public abstract String getTenantId();

  /**
   * Returns the display name of this tenant.
   *
   * @return a non-empty display name string.
   */
  public abstract String getDisplayName();

  /**
   * Returns whether to allow email/password user authentication.
   *
   * @return true if a user can be authenticated using an email and password, and false otherwise.
   */
  public abstract boolean isPasswordSignUpAllowed();

  /**
   * Returns whether to enable email link user authentication.
   *
   * @return true if a user can be authenticated using an email link, and false otherwise.
   */
  public abstract boolean isEmailLinkSignInEnabled();

  /**
   * Returns a tenant builder.
   */
  public static Builder newBuilder() {
    return new AutoValue_Tenant.Builder();
  }

  /**
   * Builder class used to construct a tenant.
   */
  @AutoValue.Builder
  abstract static class Builder {
    public abstract Builder setTenantId(String tenantId);

    public abstract Builder setDisplayName(String displayName);

    public abstract Builder setPasswordSignUpAllowed(
        boolean requirePasswordForEmailLinkSignIn);

    public abstract Builder setEmailLinkSignInEnabled(boolean enableEmailLinkSignIn);

    public abstract Tenant build();
  }
}
