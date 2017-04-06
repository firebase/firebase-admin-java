package com.google.firebase.database.snapshot;

import java.util.Map;

public class DeferredValueNode extends LeafNode<DeferredValueNode> {

  private Map<Object, Object> value;

  public DeferredValueNode(Map<Object, Object> value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Object getValue() {
    return value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return getPriorityHash(version) + "deferredValue:" + value;
  }

  @Override
  public DeferredValueNode updatePriority(Node priority) {
    assert PriorityUtilities.isValidPriority(priority);
    return new DeferredValueNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.DeferredValue;
  }

  @Override
  protected int compareLeafValues(DeferredValueNode other) {
    // Deferred value nodes are always equal
    return 0;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DeferredValueNode)) {
      return false;
    }
    DeferredValueNode otherDeferredValueNode = (DeferredValueNode) other;
    return value.equals(otherDeferredValueNode.value)
        && priority.equals(otherDeferredValueNode.priority);
  }

  @Override
  public int hashCode() {
    return value.hashCode() + this.priority.hashCode();
  }
}
