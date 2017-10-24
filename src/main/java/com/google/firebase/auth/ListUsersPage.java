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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.gax.paging.Page;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.internal.NonNull;
import java.util.List;

public class ListUsersPage implements Page<ExportedUserRecord> {

  static final String END_OF_LIST = "";

  private final UserSource source;
  private final int maxResults;

  private final ListUsersResult currentBatch;

  private ListUsersPage(@NonNull UserSource source, int maxResults, String pageToken) {
    this.source = checkNotNull(source, "source must not be null");
    checkArgument(maxResults > 0 && maxResults <= FirebaseUserManager.MAX_LIST_USERS_RESULTS,
        "maxResults must be a positive integer that does not exceed "
            + FirebaseUserManager.MAX_LIST_USERS_RESULTS);
    this.maxResults = maxResults;
    this.currentBatch = source.fetch(maxResults, pageToken);
  }

  @Override
  public boolean hasNextPage() {
    return !END_OF_LIST.equals(currentBatch.getNextPageToken());
  }

  @Override
  public String getNextPageToken() {
    return currentBatch.getNextPageToken();
  }

  @Override
  public ListUsersPage getNextPage() {
    if (hasNextPage()) {
      return new ListUsersPage(source, maxResults, currentBatch.getNextPageToken());
    }
    return null;
  }

  @Override
  public Iterable<ExportedUserRecord> iterateAll() {
    return new UserIterable(this);
  }

  @Override
  public Iterable<ExportedUserRecord> getValues() {
    return currentBatch.getUsers();
  }

  interface UserSource {
    @NonNull
    ListUsersResult fetch(int maxResults, String pageToken);
  }

  static class DefaultUserSource implements UserSource {

    private final FirebaseUserManager userManager;

    DefaultUserSource(FirebaseUserManager userManager) {
      this.userManager = checkNotNull(userManager, "user manager must not be null");
    }

    @Override
    public ListUsersResult fetch(int maxResults, String pageToken) {
      try {
        DownloadAccountResponse response = userManager.listUsers(maxResults, pageToken);
        ImmutableList.Builder<ExportedUserRecord> builder = ImmutableList.builder();
        if (response.getUsers() != null) {
          for (DownloadAccountResponse.User user : response.getUsers()) {
            builder.add(new ExportedUserRecord(user));
          }
        }
        String nextPageToken = response.getPageToken() != null
            ? response.getPageToken() : END_OF_LIST;
        return new ListUsersResult(builder.build(), nextPageToken);
      } catch (Exception e) {
        throw new RuntimeException("Error while downloading user accounts", e);
      }
    }
  }

  static final class ListUsersResult {

    private final List<ExportedUserRecord> users;
    private final String nextPageToken;

    ListUsersResult(@NonNull List<ExportedUserRecord> users, @NonNull String nextPageToken) {
      this.users = checkNotNull(users);
      this.nextPageToken = checkNotNull(nextPageToken); // Can be empty
    }

    @NonNull
    List<ExportedUserRecord> getUsers() {
      return users;
    }

    @NonNull
    String getNextPageToken() {
      return nextPageToken;
    }
  }

  static class PageFactory {

    private final UserSource source;
    private final int maxResults;
    private final String pageToken;

    PageFactory(@NonNull UserSource source) {
      this(source, FirebaseUserManager.MAX_LIST_USERS_RESULTS, null);
    }

    PageFactory(@NonNull UserSource source, int maxResults, @Nullable String pageToken) {
      checkArgument(maxResults > 0 && maxResults <= FirebaseUserManager.MAX_LIST_USERS_RESULTS,
          "maxResults must be a positive integer that does not exceed %s",
          FirebaseUserManager.MAX_LIST_USERS_RESULTS);
      checkArgument(!END_OF_LIST.equals(pageToken), "invalid end of list page token");
      this.source = checkNotNull(source, "source must not be null");
      this.maxResults = maxResults;
      this.pageToken = pageToken;
    }

    ListUsersPage create() {
      return new ListUsersPage(source, maxResults, pageToken);
    }
  }
}
