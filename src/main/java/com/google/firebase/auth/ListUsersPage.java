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

import com.google.api.client.json.JsonFactory;
import com.google.api.gax.paging.Page;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents a page of {@link ExportedUserRecord} instances. Provides methods for iterating
 * over the users in the current page, and calling up subsequent pages of users. Instances of
 * this class are thread-safe and immutable.
 */
public class ListUsersPage implements Page<ExportedUserRecord> {

  static final String END_OF_LIST = "";

  private final ListUsersResult currentBatch;
  private final UserSource source;
  private final int maxResults;

  private ListUsersPage(
      @NonNull ListUsersResult currentBatch, @NonNull UserSource source, int maxResults) {
    this.currentBatch = checkNotNull(currentBatch);
    this.source = checkNotNull(source);
    this.maxResults = maxResults;
  }

  /**
   * Checks if there is another page of users available to retrieve.
   *
   * @return true if another page is available, or false otherwise.
   */
  @Override
  public boolean hasNextPage() {
    return !END_OF_LIST.equals(currentBatch.getNextPageToken());
  }

  /**
   * Returns the string token that identifies the next page. Never returns null. Returns empty
   * string if there are no more pages available to be retrieved.
   *
   * @return A non-null string token (possibly empty, representing no more pages)
   */
  @NonNull
  @Override
  public String getNextPageToken() {
    return currentBatch.getNextPageToken();
  }

  /**
   * Returns the next page of users.
   *
   * @return A new {@link ListUsersPage} instance, or null if there are no more pages.
   */
  @Nullable
  @Override
  public ListUsersPage getNextPage() {
    if (hasNextPage()) {
      PageFactory factory = new PageFactory(source, maxResults, currentBatch.getNextPageToken());
      return factory.create();
    }
    return null;
  }

  /**
   * Returns an {@code Iterable} that facilitates transparently iterating over all the users in the
   * current Firebase project, starting from this page. The {@code Iterator} instances produced
   * by the returned {@code Iterable} never buffers more than one page of users at a time. It is
   * safe to abandon the iterators (i.e. break the loops) at any time.
   *
   * @return a new {@code Iterable<ExportedUserRecord>} instance.
   */
  @NonNull
  @Override
  public Iterable<ExportedUserRecord> iterateAll() {
    return new UserIterable(this);
  }

  /**
   * Returns an {@code Iterable} over the users in this page.
   *
   * @return a {@code Iterable<ExportedUserRecord>} instance.
   */
  @NonNull
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

  /**
   * Represents a source of user data that can be queried to load a batch of users.
   */
  interface UserSource {
    @NonNull
    ListUsersResult fetch(int maxResults, String pageToken);
  }

  static class DefaultUserSource implements UserSource {

    private final FirebaseUserManager userManager;
    private final JsonFactory jsonFactory;

    DefaultUserSource(FirebaseUserManager userManager, JsonFactory jsonFactory) {
      this.userManager = checkNotNull(userManager, "user manager must not be null");
      this.jsonFactory = checkNotNull(jsonFactory, "json factory must not be null");
    }

    @Override
    public ListUsersResult fetch(int maxResults, String pageToken) {
      try {
        DownloadAccountResponse response = userManager.listUsers(maxResults, pageToken);
        ImmutableList.Builder<ExportedUserRecord> builder = ImmutableList.builder();
        if (response.hasUsers()) {
          for (DownloadAccountResponse.User user : response.getUsers()) {
            builder.add(new ExportedUserRecord(user, jsonFactory));
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

  /**
   * A simple factory class for {@link ListUsersPage} instances. Performs argument validation
   * before attempting to load any user data (which is expensive, and hence may be performed
   * asynchronously on a separate thread).
   */
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
      ListUsersResult batch = source.fetch(maxResults, pageToken);
      return new ListUsersPage(batch, source, maxResults);
    }
  }
}
