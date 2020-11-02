/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.TemplateResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.VersionResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Represents a Remote Config template version.
 * Output only, except for the version description. Contains metadata about a particular
 * version of the Remote Config template. All fields are set at the time the specified Remote
 * Config template is published. A version's description field may be specified when
 * publishing a template.
 */
public final class Version {

  private String versionNumber;
  private long updateTime;
  private String updateOrigin;
  private String updateType;
  private User updateUser;
  private String description;
  private String rollbackSource;
  private boolean legacy;

  /**
   * Creates a new {@link Version} with a description.
   */
  public static Version withDescription(String description) {
    return new Version().setDescription(description);
  }

  Version() {
  }

  Version(@NonNull VersionResponse versionResponse) {
    checkNotNull(versionResponse);
    this.versionNumber = versionResponse.getVersionNumber();
    if (!Strings.isNullOrEmpty(versionResponse.getUpdateTime())) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      try {
        this.updateTime = dateFormat.parse(versionResponse.getUpdateTime()).getTime();
      } catch (ParseException e) {
        this.updateTime = 0;
      }
    }
    this.updateOrigin = versionResponse.getUpdateOrigin();
    this.updateType = versionResponse.getUpdateType();
    TemplateResponse.UserResponse userResponse = versionResponse.getUpdateUser();
    this.updateUser = (userResponse != null) ? new User(userResponse) : null;
    this.description = versionResponse.getDescription();
    this.rollbackSource = versionResponse.getRollbackSource();
    this.legacy = versionResponse.isLegacy();
  }

  /**
   * Gets the version number of the template.
   *
   * @return The version number or null.
   */
  @Nullable
  public String getVersionNumber() {
    return versionNumber;
  }

  /**
   * Gets the update time of the version. The timestamp of when this version of the Remote Config
   * template was written to the Remote Config backend.
   *
   * @return The update time of the version or null.
   */
  @Nullable
  public long getUpdateTime() {
    return updateTime;
  }

  /**
   * Gets the origin of the template update action.
   *
   * @return The origin of the template update action or null.
   */
  @Nullable
  public String getUpdateOrigin() {
    return updateOrigin;
  }

  /**
   * Gets the type of the template update action.
   *
   * @return The type of the template update action or null.
   */
  @Nullable
  public String getUpdateType() {
    return updateType;
  }

  /**
   * Gets the update user of the template.
   * An aggregation of all metadata fields about the account that performed the update.
   *
   * @return The update user of the template or null.
   */
  @Nullable
  public User getUpdateUser() {
    return updateUser;
  }

  /**
   * Gets the user-provided description of the corresponding Remote Config template.
   *
   * @return The description of the template or null.
   */
  @Nullable
  public String getDescription() {
    return description;
  }

  /**
   * Gets the rollback source of the template.
   *
   * <p>The version number of the Remote Config template that has become the current version
   * due to a rollback. Only present if this version is the result of a rollback.
   *
   * @return The rollback source of the template or null.
   */
  @Nullable
  public String getRollbackSource() {
    return rollbackSource;
  }

  /**
   * Indicates whether this Remote Config template was published before version history was
   * supported.
   *
   * @return true if the template was published before version history was supported,
   *     and false otherwise.
   */
  public boolean isLegacy() {
    return legacy;
  }

  /**
   * Sets the user-provided description of the template.
   *
   * @param description The description of the template.
   * @return This {@link Version}.
   */
  public Version setDescription(String description) {
    this.description = description;
    return this;
  }

  VersionResponse toVersionResponse() {
    return new VersionResponse()
            .setDescription(this.description);
  }
}
