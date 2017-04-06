package com.google.firebase.database.snapshot;

public class KeyIndex extends Index {

  private static final KeyIndex INSTANCE = new KeyIndex();

  private KeyIndex() {
    // prevent instantiation
  }

  public static KeyIndex getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isDefinedOn(Node a) {
    return true;
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    assert value instanceof StringNode;
    // We just use empty node, but it'll never be compared, since our comparator only looks at name
    return new NamedNode(ChildKey.fromString((String) value.getValue()), EmptyNode.Empty());
  }

  @Override
  public NamedNode maxPost() {
    return NamedNode.getMaxNode();
  }

  @Override
  public String getQueryDefinition() {
    return ".key";
  }

  @Override
  public int compare(NamedNode o1, NamedNode o2) {
    return o1.getName().compareTo(o2.getName());
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof KeyIndex;
  }

  @Override
  public int hashCode() {
    // chosen by a fair dice roll. Guaranteed to be random
    return 37;
  }

  @Override
  public String toString() {
    return "KeyIndex";
  }
}
