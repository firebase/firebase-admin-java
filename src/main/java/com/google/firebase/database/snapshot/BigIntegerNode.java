/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    return new BigInteger(this.value);
  }

  @Override
  public String getHashRepresentation(HashVersion version) {
    return getPriorityHash(version) + "bigdecimal:" + this.value;
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.Number;
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
