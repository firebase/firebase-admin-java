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

package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.view.QueryParams;

public class OperationSource {

  public static final OperationSource USER = new OperationSource(Source.User, null, false);
  public static final OperationSource SERVER = new OperationSource(Source.Server, null, false);
  private final Source source;
  private final QueryParams queryParams;
  private final boolean tagged;

  public OperationSource(Source source, QueryParams queryParams, boolean tagged) {
    this.source = source;
    this.queryParams = queryParams;
    this.tagged = tagged;
    assert !tagged || isFromServer();
  }

  public static OperationSource forServerTaggedQuery(QueryParams queryParams) {
    return new OperationSource(Source.Server, queryParams, true);
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

  private enum Source {
    User,
    Server
  }
}
