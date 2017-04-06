package com.google.firebase.database.snapshot;

public class NamedNode {

  private static final NamedNode MIN_NODE = new NamedNode(ChildKey.getMinName(), EmptyNode
      .Empty());
  private static final NamedNode MAX_NODE = new NamedNode(ChildKey.getMaxName(), Node.MAX_NODE);
  private final ChildKey name;
  private final Node node;

  public NamedNode(ChildKey name, Node node) {
    this.name = name;
    this.node = node;
  }

  public static NamedNode getMinNode() {
    return MIN_NODE;
  }

  public static NamedNode getMaxNode() {
    return MAX_NODE;
  }

  public ChildKey getName() {
    return this.name;
  }

  public Node getNode() {
    return this.node;
  }

  @Override
  public String toString() {
    return "NamedNode{" + "name=" + name + ", node=" + node + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NamedNode namedNode = (NamedNode) o;

    if (!name.equals(namedNode.name)) {
      return false;
    }
    if (!node.equals(namedNode.node)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + node.hashCode();
    return result;
  }
}
