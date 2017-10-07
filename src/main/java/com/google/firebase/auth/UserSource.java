package com.google.firebase.auth;


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
   * @return A non-null {@link ListUsersResult} instance
   * @throws Exception If an error occurs during the fetch
   */
  @NonNull
  ListUsersResult fetch(int maxResults, PageToken pageToken) throws Exception;
}
