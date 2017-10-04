package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.internal.NonNull;

/**
 * A UserSource can be queried to bulk fetch the user accounts associated with a Firebase project.
 */
interface UserSource {

  /**
   * Fetch up to {@code maxResults} user accounts from this source, starting from the offset
   * indicated by the {@code pageToken}.
   *
   * @param maxResults Maximum number of accounts to fetch
   * @param pageToken Starting point (offset) for the bulk fetch operation
   * @return A non-null {@link FetchResult} instance
   * @throws Exception If an error occurs during the fetch
   */
  @NonNull
  FetchResult fetch(int maxResults, String pageToken) throws Exception;

  final class FetchResult {

    private final ImmutableList<ExportedUserRecord> users;
    private final String nextPageToken;

    FetchResult(ImmutableList<ExportedUserRecord> users, String nextPageToken) {
      this.users = checkNotNull(users);
      if (nextPageToken != null) {
        checkArgument(!"".equals(nextPageToken), "Next page token must not be empty");
      }
      this.nextPageToken = nextPageToken;
    }

    /**
     * List of users fetched by a source. The list may be empty, but is never null.
     *
     * @return A non-null List.
     */
    @NonNull
    ImmutableList<ExportedUserRecord> getUsers() {
      return users;
    }

    /**
     * Offset for the next batch of users that can be fetched from the same source that produced
     * this FetchResult. This essentially plays the role of a moving cursor, where the cursor is
     * advanced by the source on each fetch operation. A null value indicates that all the user
     * accounts have been fetched (i.e. end of list).
     *
     * @return A string page token value, possibly null.
     */
    @Nullable
    String getNextPageToken() {
      return nextPageToken;
    }

    boolean isEndOfList() {
      return nextPageToken == null;
    }
  }
}
