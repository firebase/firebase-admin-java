package com.google.firebase.database.snapshot;

public class ValueIndex extends Index {

  private static final ValueIndex INSTANCE = new ValueIndex();

  private ValueIndex() {
    // prevent initialization
  }

  public static ValueIndex getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isDefinedOn(Node a) {
    return true;
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    return new NamedNode(name, value);
  }

  @Override
  public NamedNode maxPost() {
    return new NamedNode(ChildKey.getMaxName(), Node.MAX_NODE);
  }

  @Override
  public String getQueryDefinition() {
    return ".value";
  }

  @Override
  public int compare(NamedNode one, NamedNode two) {
    int indexCmp = one.getNode().compareTo(two.getNode());
    if (indexCmp == 0) {
      return one.getName().compareTo(two.getName());
    } else {
      return indexCmp;
    }
  }

  @Override
  public int hashCode() {
    // chosen by fair dice roll
    return 4;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ValueIndex;
  }

  @Override
  public String toString() {
    return "ValueIndex";
  }
}
