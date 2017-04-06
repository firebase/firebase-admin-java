package com.google.firebase.database.snapshot;

public class PriorityIndex extends Index {

  private static final PriorityIndex INSTANCE = new PriorityIndex();

  public static PriorityIndex getInstance() {
    return INSTANCE;
  }

  private PriorityIndex() {
    // prevent creation
  }

  @Override
  public int compare(NamedNode a, NamedNode b) {
    Node aPrio = a.getNode().getPriority();
    Node bPrio = b.getNode().getPriority();
    return NodeUtilities.nameAndPriorityCompare(a.getName(), aPrio, b.getName(), bPrio);
  }

  @Override
  public boolean isDefinedOn(Node a) {
    return !a.getPriority().isEmpty();
  }

  @Override
  public NamedNode makePost(ChildKey name, Node value) {
    return new NamedNode(name, new StringNode("[PRIORITY-POST]", value));
  }

  @Override
  public NamedNode maxPost() {
    return makePost(ChildKey.getMaxName(), Node.MAX_NODE);
  }

  @Override
  public String getQueryDefinition() {
    throw new IllegalArgumentException("Can't get query definition on priority index!");
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PriorityIndex;
  }

  @Override
  public int hashCode() {
    // chosen by a fair dice roll. Guaranteed to be random
    return 3155577;
  }

  @Override
  public String toString() {
    return "PriorityIndex";
  }
}
