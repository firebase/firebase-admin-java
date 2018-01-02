/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.auth.internal;

import com.google.api.client.util.Key;
import java.util.List;

/**
 * JSON data binding for downloadAccountResponse messages sent by Google identity toolkit service.
 */
public class DownloadAccountResponse {

  @Key("users")
  private List<User> users;

  @Key("nextPageToken")
  private String pageToken;

  public List<User> getUsers() {
    return users;
  }

  public boolean hasUsers() {
    return users != null && !users.isEmpty();
  }

  public String getPageToken() {
    return pageToken;
  }

  /**
   * JSON data binding for exported user records.
   */
  public static final class User extends GetAccountInfoResponse.User {

    @Key("passwordHash")
    private String passwordHash;

    @Key("salt")
    private String passwordSalt;

    public String getPasswordHash() {
      return passwordHash;
    }

    public String getPasswordSalt() {
      return passwordSalt;
    }
  }
}
