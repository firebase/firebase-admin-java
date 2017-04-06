package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.view.QueryParams;

public class OperationSource {

  private enum Source {
    User,
    Server
  }

  public static final OperationSource USER = new OperationSource(Source.User, null, false);
  public static final OperationSource SERVER = new OperationSource(Source.Server, null, false);

  public static OperationSource forServerTaggedQuery(QueryParams queryParams) {
    return new OperationSource(Source.Server, queryParams, true);
  }

  private final Source source;
  private final QueryParams queryParams;
  private final boolean tagged;

  public OperationSource(Source source, QueryParams queryParams, boolean tagged) {
    this.source = source;
    this.queryParams = queryParams;
    this.tagged = tagged;
    assert !tagged || isFromServer();
  }

  public boolean isFromUser() {
    return this.source == Source.User;
  }

  public boolean isFromServer() {
    return this.source == Source.Server;
  }

  public boolean isTagged() {
    return tagged;
  }

  @Override
  public String toString() {
    return "OperationSource{"
        + "source="
        + source
        + ", queryParams="
        + queryParams
        + ", tagged="
        + tagged
        + '}';
  }

  public QueryParams getQueryParams() {
    return this.queryParams;
  }
}
