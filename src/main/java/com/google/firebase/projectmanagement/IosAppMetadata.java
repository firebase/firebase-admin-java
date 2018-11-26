/* Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import com.google.api.client.util.Preconditions;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Contains detailed information about an iOS App. Instances of this class are immutable.
 */
public class IosAppMetadata {
  private final String name;
  private final String appId;
  private final String displayName;
  private final String projectId;
  private final String bundleId;

  IosAppMetadata(
      String name, String appId, String displayName, String projectId, String bundleId) {
    this.name = Preconditions.checkNotNull(name, "Null name");
    this.appId = Preconditions.checkNotNull(appId, "Null appId");
    this.displayName = Preconditions.checkNotNull(displayName, "Null displayName");
    this.projectId = Preconditions.checkNotNull(projectId, "Null projectId");
    this.bundleId = Preconditions.checkNotNull(bundleId, "Null bundleId");
  }

  /**
   * Returns the fully qualified resource name of this iOS App.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the globally unique, Firebase-assigned identifier of this iOS App. This ID is unique
   * even across Apps of different platforms, such as Android Apps.
   */
  public String getAppId() {
    return appId;
  }

  /**
   * Returns the user-assigned display name of this iOS App.
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns the permanent, globally unique, user-assigned ID of the parent Project for this iOS
   * App.
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Returns the canonical bundle ID of this iOS App as it would appear in the iOS AppStore.
   */
  public String getBundleId() {
    return bundleId;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("IosAppMetadata")
        .add("name", name)
        .add("appId", appId)
        .add("displayName", displayName)
        .add("projectId", projectId)
        .add("bundleId", bundleId)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof IosAppMetadata) {
      IosAppMetadata that = (IosAppMetadata) o;
      return Objects.equal(this.name, that.name)
          && Objects.equal(this.appId, that.appId)
          && Objects.equal(this.displayName, that.displayName)
          && Objects.equal(this.projectId, that.projectId)
          && Objects.equal(this.bundleId, that.bundleId);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, appId, displayName, projectId, bundleId);
  }
}
