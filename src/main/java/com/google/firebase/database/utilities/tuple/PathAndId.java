package com.google.firebase.database.utilities.tuple;

import com.google.firebase.database.core.Path;

/** User: greg Date: 5/22/13 Time: 12:21 PM */
public class PathAndId {

  private Path path;
  private long id;

  public PathAndId(Path path, long id) {
    this.path = path;
    this.id = id;
  }

  public Path getPath() {
    return path;
  }

  public long getId() {
    return id;
  }
}
