package com.google.firebase.database.snapshot;

import java.math.BigDecimal;

public class BigDecimalNode extends LeafNode<BigDecimalNode> {

  private final String value;

  public BigDecimalNode(BigDecimal value, Node priority) {
    this(value == null ? null : value.toString(), priority);
  }

  BigDecimalNode(String value, Node priority) {
    super(priority);
    this.value = value;
  }

  @Override
  public Node updatePriority(Node priority) {
    return new BigDecimalNode(this.value, priority);
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
  protected int compareLeafValues(BigDecimalNode other) {
    if (other == null) {
      return 1;
    }
    return this.value.compareTo(other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof BigDecimalNode)) {
      return false;
    }
    BigDecimalNode otherBigDecimalNode = (BigDecimalNode) other;
    return this.value.equals(otherBigDecimalNode.value)
        && this.priority.equals(otherBigDecimalNode.priority);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode() + this.priority.hashCode();
  }
}
