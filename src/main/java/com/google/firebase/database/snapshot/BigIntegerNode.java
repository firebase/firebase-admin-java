package com.google.firebase.database.snapshot;

import java.math.BigInteger;

public class BigIntegerNode extends LeafNode<BigIntegerNode> {

  private final String value;

  public BigIntegerNode(BigInteger value, Node priority) {
    this(value == null ? null : value.toString(), priority);
  }

  BigIntegerNode(String value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Node updatePriority(Node priority) {
    return new BigIntegerNode(this.value, priority);
  }

  @Override
  public Object getValue() {
    return this.value;
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return null;
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.String;
  }

  @Override
  protected int compareLeafValues(BigIntegerNode other) {
    if (other == null) {
      return 1;
    }
    return this.value.compareTo(other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof BigIntegerNode)) {
      return false;
    }
    BigIntegerNode otherBigDecimalNode = (BigIntegerNode) other;
    return this.value.equals(otherBigDecimalNode.value)
        && this.priority.equals(otherBigDecimalNode.priority);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode() + this.priority.hashCode();
  }
}
