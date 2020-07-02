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

package com.google.firebase.auth.multitenancy;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains metadata associated with a Firebase tenant.
 *
 * <p>Instances of this class are immutable and thread safe.
 */
public final class Tenant {

  @Key("name")
  private String resourceName;

  @Key("displayName")
  private String displayName;

  @Key("allowPasswordSignup")
  private boolean passwordSignInAllowed;

  @Key("enableEmailLinkSignin")
  private boolean emailLinkSignInEnabled;

  public String getTenantId() {
    return resourceName.substring(resourceName.lastIndexOf("/") + 1);
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isPasswordSignInAllowed() {
    return passwordSignInAllowed;
  }

  public boolean isEmailLinkSignInEnabled() {
    return emailLinkSignInEnabled;
  }

  /**
   * Returns a new {@link UpdateRequest}, which can be used to update the attributes of this tenant.
   *
   * @return a non-null {@link UpdateRequest} instance.
   */
  public UpdateRequest updateRequest() {
    return new UpdateRequest(getTenantId());
  }

  /**
   * A specification class for creating a new tenant.
   *
   * <p>Set the initial attributes of the new tenant by calling various setter methods available in
   * this class. None of the attributes are required.
   */
  public static final class CreateRequest {

    private final Map<String,Object> properties = new HashMap<>();

    /**
     * Creates a new {@link CreateRequest}, which can be used to create a new tenant.
     *
     * <p>The returned object should be passed to {@link TenantManager#createTenant(CreateRequest)}
     * to register the tenant information persistently.
     */
    public CreateRequest() { }

    /**
     * Sets the display name for the new tenant.
     *
     * @param displayName a non-null, non-empty display name string.
     */
    public CreateRequest setDisplayName(String displayName) {
      checkArgument(!Strings.isNullOrEmpty(displayName), "display name must not be null or empty");
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Sets whether to allow email/password user authentication.
     *
     * @param passwordSignInAllowed a boolean indicating whether users can be authenticated using
     *     an email and password.
     */
    public CreateRequest setPasswordSignInAllowed(boolean passwordSignInAllowed) {
      properties.put("allowPasswordSignup", passwordSignInAllowed);
      return this;
    }

    /**
     * Sets whether to enable email link user authentication.
     *
     * @param emailLinkSignInEnabled a boolean indicating whether users can be authenticated using
     *     an email link.
     */
    public CreateRequest setEmailLinkSignInEnabled(boolean emailLinkSignInEnabled) {
      properties.put("enableEmailLinkSignin", emailLinkSignInEnabled);
      return this;
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }
  }

  /**
   * A class for updating the attributes of an existing tenant.
   *
   * <p>An instance of this class can be obtained via a {@link Tenant} object, or from a tenant ID
   * string. Specify the changes to be made to the tenant by calling the various setter methods
   * available in this class.
   */
  public static final class UpdateRequest {

    private final String tenantId;
    private final Map<String,Object> properties = new HashMap<>();

    /**
     * Creates a new {@link UpdateRequest}, which can be used to update the attributes of the
     * of the tenant identified by the specified tenant ID.
     *
     * <p>This method allows updating attributes of a tenant account, without first having to call
     * {@link TenantManager#getTenant(String)}.
     *
     * @param tenantId a non-null, non-empty tenant ID string.
     * @throws IllegalArgumentException If the tenant ID is null or empty.
     */
    public UpdateRequest(String tenantId) {
      checkArgument(!Strings.isNullOrEmpty(tenantId), "tenant ID must not be null or empty");
      this.tenantId = tenantId;
    }

    String getTenantId() {
      return tenantId;
    }

    /**
     * Sets the display name of the existing tenant.
     *
     * @param displayName a non-null, non-empty display name string.
     */
    public UpdateRequest setDisplayName(String displayName) {
      checkArgument(!Strings.isNullOrEmpty(displayName), "display name must not be null or empty");
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Sets whether to allow email/password user authentication.
     *
     * @param passwordSignInAllowed a boolean indicating whether users can be authenticated using
     *     an email and password.
     */
    public UpdateRequest setPasswordSignInAllowed(boolean passwordSignInAllowed) {
      properties.put("allowPasswordSignup", passwordSignInAllowed);
      return this;
    }

    /**
     * Sets whether to enable email link user authentication.
     *
     * @param emailLinkSignInEnabled a boolean indicating whether users can be authenticated using
     *     an email link.
     */
    public UpdateRequest setEmailLinkSignInEnabled(boolean emailLinkSignInEnabled) {
      properties.put("enableEmailLinkSignin", emailLinkSignInEnabled);
      return this;
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }
  }
}
