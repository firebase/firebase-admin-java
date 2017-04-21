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

public class PriorityIndex extends Index {

  private static final PriorityIndex INSTANCE = new PriorityIndex();

  private PriorityIndex() {
    // prevent creation
  }

  public static PriorityIndex getInstance() {
    return INSTANCE;
  }

  @Override
  public int compare(NamedNode node1, NamedNode node2) {
    Node priority1 = node1.getNode().getPriority();
    Node priority2 = node2.getNode().getPriority();
    return NodeUtilities.nameAndPriorityCompare(
        node1.getName(), priority1, node2.getName(), priority2);
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
