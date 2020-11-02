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

import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.TemplateResponse.UserResponse;

/**
 * Represents a Remote Config user. Output only.
 */
public final class User {

  private String email;
  private String name;
  private String imageUrl;

  User(@NonNull UserResponse userResponse) {
    checkNotNull(userResponse);
    this.email = userResponse.getEmail();
    this.name = userResponse.getName();
    this.imageUrl = userResponse.getImageUrl();
  }

  /**
   * Gets the email of the user.
   *
   * @return The email of the user or null.
   */
  @Nullable
  public String getEmail() {
    return email;
  }

  /**
   * Gets the name of the user.
   *
   * @return The name of the user or null.
   */
  @Nullable
  public String getName() {
    return name;
  }

  /**
   * Gets the image URL of the user.
   *
   * @return The image URL of the user or null.
   */
  @Nullable
  public String getImageUrl() {
    return imageUrl;
  }
}
