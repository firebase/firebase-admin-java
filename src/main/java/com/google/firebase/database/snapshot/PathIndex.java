package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.Path;

public class PathIndex extends Index {

  private final Path indexPath;

  public PathIndex(Path indexPath) {
    if (indexPath.size() == 1 && indexPath.getFront().isPriorityChildName()) {
      throw new IllegalArgumentException(
          "Can't create PathIndex with '.priority' as key. Please use PriorityIndex " +
              "instead!");
    }
    this.indexPath = indexPath;
  }

  @Override
  public boolean isDefinedOn(Node snapshot) {
    return !snapshot.getChild(this.indexPath).isEmpty();
  }

  @Override
  public int compare(NamedNode a, NamedNode b) {
    Node aChild = a.getNode().getChild(this.indexPath);
    Node bChild = b.getNode().getChild(this.indexPath);
    int indexCmp = aChild.compareTo(bChild);
    if (indexCmp == 0) {
      return a.getName().compareTo(b.getName());
    } else {
      return indexCmp;
    }
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    Node node = EmptyNode.Empty().updateChild(this.indexPath, value);
    return new NamedNode(name, node);
  }

  @Override
  public NamedNode maxPost() {
    Node node = EmptyNode.Empty().updateChild(this.indexPath, Node.MAX_NODE);
    return new NamedNode(ChildKey.getMaxName(), node);
  }

  @Override
  public String getQueryDefinition() {
    return this.indexPath.wireFormat();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PathIndex that = (PathIndex) o;

    if (!indexPath.equals(that.indexPath)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return indexPath.hashCode();
  }
}
