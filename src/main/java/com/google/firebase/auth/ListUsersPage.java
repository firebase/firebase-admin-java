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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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

  private static class UserIterable implements Iterable<ExportedUserRecord> {

    private final ListUsersPage startingPage;

    UserIterable(@NonNull ListUsersPage startingPage) {
      this.startingPage = checkNotNull(startingPage, "starting page must not be null");
    }

    @Override
    @NonNull
    public Iterator<ExportedUserRecord> iterator() {
      return new UserIterator(startingPage);
    }

    /**
     * An {@code Iterator} that cycles through user accounts, one at a time. It buffers the
     * last retrieved batch of user accounts in memory. The {@code maxResults} parameter is an
     * upper bound on the batch size.
     */
    private static class UserIterator implements Iterator<ExportedUserRecord> {

      private ListUsersPage currentPage;
      private List<ExportedUserRecord> batch;
      private int index = 0;

      private UserIterator(ListUsersPage startingPage) {
        setCurrentPage(startingPage);
      }

      @Override
      public boolean hasNext() {
        if (index == batch.size()) {
          if (currentPage.hasNextPage()) {
            setCurrentPage(currentPage.getNextPage());
          } else {
            return false;
          }
        }

        return index < batch.size();
      }

      @Override
      public ExportedUserRecord next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return batch.get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove operation not supported");
      }

      private void setCurrentPage(ListUsersPage page) {
        this.currentPage = checkNotNull(page);
        this.batch = ImmutableList.copyOf(page.getValues());
        this.index = 0;
      }
    }
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
