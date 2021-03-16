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

import java.util.Objects;

/**
 * Represents a Remote Config user. Output only.
 */
public class User {

  private final String email;
  private final String name;
  private final String imageUrl;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return Objects.equals(email, user.email)
            && Objects.equals(name, user.name)
            && Objects.equals(imageUrl, user.imageUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(email, name, imageUrl);
  }

  UserResponse toUserResponse() {
    return new UserResponse()
            .setEmail(this.email)
            .setImageUrl(this.imageUrl)
            .setName(this.name);
  }
}
