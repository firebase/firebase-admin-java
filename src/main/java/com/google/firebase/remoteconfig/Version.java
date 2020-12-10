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
import java.util.Objects;

/**
 * Represents a Remote Config template version.
 * Output only, except for the version description. Contains metadata about a particular
 * version of the Remote Config template. All fields are set at the time the specified Remote
 * Config template is published. A version's description field may be specified when
 * publishing a template.
 */
public final class Version {

  private final String versionNumber;
  private final long updateTime;
  private final String updateOrigin;
  private final String updateType;
  private final User updateUser;
  private final String rollbackSource;
  private final boolean legacy;
  private String description;

  private Version() {
    this.versionNumber = null;
    this.updateTime = 0L;
    this.updateOrigin = null;
    this.updateType = null;
    this.updateUser = null;
    this.rollbackSource = null;
    this.legacy = false;
  }

  Version(@NonNull VersionResponse versionResponse) {
    checkNotNull(versionResponse);
    this.versionNumber = versionResponse.getVersionNumber();

    if (!Strings.isNullOrEmpty(versionResponse.getUpdateTime())) {
      try {
        this.updateTime = RemoteConfigUtil.convertToMilliseconds(versionResponse.getUpdateTime());
      } catch (ParseException e) {
        throw new IllegalStateException("Unable to parse update time.", e);
      }
    } else {
      this.updateTime = 0L;
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
   * Creates a new {@link Version} with a description.
   */
  public static Version withDescription(String description) {
    return new Version().setDescription(description);
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

  VersionResponse toVersionResponse(boolean includeAll) {
    VersionResponse versionResponse = new VersionResponse().setDescription(this.description);
    if (includeAll) {
      versionResponse.setUpdateTime(this.updateTime > 0L
              ? RemoteConfigUtil.convertToUtcDateFormat(this.updateTime) : null)
              .setLegacy(this.legacy)
              .setRollbackSource(this.rollbackSource)
              .setUpdateOrigin(this.updateOrigin)
              .setUpdateType(this.updateType)
              .setUpdateUser((this.updateUser == null) ? null : this.updateUser.toUserResponse())
              .setVersionNumber(this.versionNumber);
    }
    return versionResponse;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Version version = (Version) o;
    return updateTime == version.updateTime
            && legacy == version.legacy
            && Objects.equals(versionNumber, version.versionNumber)
            && Objects.equals(updateOrigin, version.updateOrigin)
            && Objects.equals(updateType, version.updateType)
            && Objects.equals(updateUser, version.updateUser)
            && Objects.equals(description, version.description)
            && Objects.equals(rollbackSource, version.rollbackSource);
  }

  @Override
  public int hashCode() {
    return Objects
            .hash(versionNumber, updateTime, updateOrigin, updateType, updateUser, description,
                    rollbackSource, legacy);
  }
}
