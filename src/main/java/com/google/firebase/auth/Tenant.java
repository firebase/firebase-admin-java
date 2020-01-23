/*
 * Copyright 2020 Google LLC
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

import com.google.api.client.util.Key;
import com.google.auto.value.AutoValue;

/**
 * Contains metadata associated with a Firebase tenant.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class Tenant {

  @Key("tenantId")
  private String tenantId;

  @Key("displayName")
  private String displayName;

  @Key("allowPasswordSignup")
  private String passwordSignInAllowed;

  @Key("enableEmailLinkSignin")
  private String emailLinkSignInEnabled;

  /**
   * Class used to hold the information needs to make a tenant create request.
   */
  @AutoValue
  public abstract static class CreateRequest {

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
    public abstract boolean isPasswordSignInAllowed();

    /**
     * Returns whether to enable email link user authentication.
     *
     * @return true if a user can be authenticated using an email link, and false otherwise.
     */
    public abstract boolean isEmailLinkSignInEnabled();

    /**
     * Returns a builder for a tenant create request.
     */
    public static Builder newBuilder() {
      return new AutoValue_Tenant_CreateRequest.Builder();
    }

    /**
   * Builder class used to construct a create request.
   */
    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setDisplayName(String displayName);

      public abstract Builder setPasswordSignInAllowed(boolean allowPasswordSignIn);

      public abstract Builder setEmailLinkSignInEnabled(boolean enableEmailLinkSignIn);

      public abstract CreateRequest build();
    }
  }

  /**
   * Class used to hold the information needs to make a tenant update request. 
   */
  @AutoValue
  public abstract static class UpdateRequest {

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
    public abstract boolean isPasswordSignInAllowed();

    /**
     * Returns whether to enable email link user authentication.
     *
     * @return true if a user can be authenticated using an email link, and false otherwise.
     */
    public abstract boolean isEmailLinkSignInEnabled();

    /**
     * Returns a builder for a tenant update request.
     */
    public static Builder newBuilder() {
      return new AutoValue_Tenant_UpdateRequest.Builder();
    }

    /**
   * Builder class used to construct a update request.
   */
    @AutoValue.Builder
    abstract static class Builder {
      public abstract Builder setDisplayName(String displayName);

      public abstract Builder setPasswordSignInAllowed(boolean allowPasswordSignIn);

      public abstract Builder setEmailLinkSignInEnabled(boolean enableEmailLinkSignIn);

      public abstract UpdateRequest build();
    }
  }
}
