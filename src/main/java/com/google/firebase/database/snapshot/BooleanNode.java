package com.google.firebase.database.snapshot;

public class BooleanNode extends LeafNode<BooleanNode> {

  private final boolean value;

  public BooleanNode(Boolean value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return getPriorityHash(version) + "boolean:" + value;
  }

  @Override
  public BooleanNode updatePriority(Node priority) {
    return new BooleanNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.Boolean;
  }

  @Override
  protected int compareLeafValues(BooleanNode other) {
    return this.value == other.value ? 0 : (value ? 1 : -1);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof BooleanNode)) {
      return false;
    }
    BooleanNode otherBooleanNode = (BooleanNode) other;
    return value == otherBooleanNode.value && priority.equals(otherBooleanNode.priority);
  }

  @Override
  public int hashCode() {
    return (this.value ? 1 : 0) + this.priority.hashCode();
  }
}
