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
    this.users = users;
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
