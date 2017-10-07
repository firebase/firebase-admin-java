package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

public final class PageToken {

  private final String token;

  public PageToken(String token) {
    if (token != null) {
      checkArgument(!token.isEmpty(), "page token must not be empty");
    }
    this.token = token;
  }

  public boolean isEndOfList() {
    return token == null;
  }

  @Override
  public String toString() {
    return token;
  }
}
