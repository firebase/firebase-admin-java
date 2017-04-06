package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.Index;
import java.util.Map;

public class QuerySpec {

  public static QuerySpec defaultQueryAtPath(Path path) {
    return new QuerySpec(path, QueryParams.DEFAULT_PARAMS);
  }

  public QuerySpec(Path path, QueryParams params) {
    this.path = path;
    this.params = params;
  }

  private final Path path;
  private final QueryParams params;

  public Path getPath() {
    return this.path;
  }

  public QueryParams getParams() {
    return this.params;
  }

  public static QuerySpec fromPathAndQueryObject(Path path, Map<String, Object> map) {
    QueryParams params = QueryParams.fromQueryObject(map);
    return new QuerySpec(path, params);
  }

  public Index getIndex() {
    return this.params.getIndex();
  }

  public boolean isDefault() {
    return this.params.isDefault();
  }

  public boolean loadsAllData() {
    return this.params.loadsAllData();
  }

  @Override
  public String toString() {
    return this.path + ":" + params;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QuerySpec that = (QuerySpec) o;

    if (!path.equals(that.path)) {
      return false;
    }
    if (!params.equals(that.params)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = path.hashCode();
    result = 31 * result + params.hashCode();
    return result;
  }
}
