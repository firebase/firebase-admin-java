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

import com.google.firebase.database.utilities.Utilities;

/** User: greg Date: 5/17/13 Time: 2:51 PM */
public class DoubleNode extends LeafNode<DoubleNode> {

  private final Double value;

  public DoubleNode(Double value, Node priority) {
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
    toHash += Utilities.doubleToHashString(value);
    return toHash;
  }

  @Override
  public DoubleNode updatePriority(Node priority) {
    assert PriorityUtilities.isValidPriority(priority);
    return new DoubleNode(value, priority);
  }

  @Override
  protected LeafType getLeafType() {
    return LeafType.Number;
  }

  @Override
  protected int compareLeafValues(DoubleNode other) {
    // TODO: unify number nodes
    return this.value.compareTo(other.value);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DoubleNode)) {
      return false;
    }
    DoubleNode otherDoubleNode = (DoubleNode) other;
    return value.equals(otherDoubleNode.value) && priority.equals(otherDoubleNode.priority);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode() + this.priority.hashCode();
  }
}
