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

package com.google.firebase.auth;

import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.internal.NonNull;

import java.util.List;

public final class ListUsersResult {

  private final List<ExportedUserRecord> users;
  private final PageToken nextPageToken;

  ListUsersResult(DownloadAccountResponse response) {
    ImmutableList.Builder<ExportedUserRecord> builder = ImmutableList.builder();
    if (response.getUsers() != null) {
      for (DownloadAccountResponse.User user : response.getUsers()) {
        builder.add(new ExportedUserRecord(user));
      }
    }
    this.users = builder.build();
    this.nextPageToken = new PageToken(response.getPageToken());
  }

  ListUsersResult(List<ExportedUserRecord> users, String tokenString) {
    this.users = users == null ? ImmutableList.<ExportedUserRecord>of() : users;
    this.nextPageToken = new PageToken(tokenString);
  }

  @NonNull
  public List<ExportedUserRecord> getUsers() {
    return users;
  }

  @NonNull
  public PageToken getNextPageToken() {
    return nextPageToken;
  }

  public boolean isEndOfList() {
    return nextPageToken != null && nextPageToken.isEndOfList();
  }
}
