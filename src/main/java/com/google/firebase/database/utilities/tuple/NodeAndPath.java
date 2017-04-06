package com.google.firebase.database.utilities.tuple;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.Node;

/**
 * User: greg Date: 5/22/13 Time: 8:35 AM
 */
public class NodeAndPath {

  private Node node;
  private Path path;

  public NodeAndPath(Node node, Path path) {
    this.node = node;
    this.path = path;
  }

  public Node getNode() {
    return node;
  }

  public void setNode(Node node) {
    this.node = node;
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }
}
