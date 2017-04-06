package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;

/**
 * User: greg Date: 5/16/13 Time: 4:11 PM
 */
public class SnapshotHolder {

  private Node rootNode;

  SnapshotHolder() {
    rootNode = EmptyNode.Empty();
  }

  public SnapshotHolder(Node node) {
    rootNode = node;
  }

  public Node getRootNode() {
    return rootNode;
  }

  public Node getNode(Path path) {
    return rootNode.getChild(path);
  }

  public void update(Path path, Node node) {
    rootNode = rootNode.updateChild(path, node);
  }
}
