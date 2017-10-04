package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;

interface UserFetcher {

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

    ImmutableList<ExportedUserRecord> getUsers() {
      return users;
    }

    String getNextPageToken() {
      return nextPageToken;
    }

    boolean isEndOfList() {
      return nextPageToken == null;
    }
  }
}
