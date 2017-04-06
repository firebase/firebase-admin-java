package com.google.firebase.database.snapshot;

import com.google.firebase.database.utilities.Utilities;

/**
 * User: greg Date: 5/17/13 Time: 2:47 PM
 */
public class LongNode extends LeafNode<LongNode> {

  private final long value;

  public LongNode(Long value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    String toHash = getPriorityHash(version);
    toHash += "number:";
    toHash += Utilities.doubleToHashString((double) value);
    return toHash;
  }

  @Override
  public LongNode updatePriority(Node priority) {
    return new LongNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    // TODO: unify number nodes
    return LeafType.Number;
  }

  @Override
  protected int compareLeafValues(LongNode other) {
    return Utilities.compareLongs(this.value, other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof LongNode)) {
      return false;
    }
    LongNode otherLongNode = (LongNode) other;
    return value == otherLongNode.value && priority.equals(otherLongNode.priority);
  }

  @Override
  public int hashCode() {
    return (int) (value ^ (value >>> 32)) + priority.hashCode();
  }
}
