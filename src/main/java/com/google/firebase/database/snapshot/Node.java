package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.Path;

import java.util.Iterator;

/**
 * User: greg Date: 5/16/13 Time: 4:38 PM
 */
public interface Node extends Comparable<Node>, Iterable<NamedNode> {

  ChildrenNode MAX_NODE =
      new ChildrenNode() {
        @Override
        public int compareTo(Node other) {
          return (other == this) ? 0 : 1;
        }

        @Override
        public boolean equals(Object other) {
          return other == this;
        }

        @Override
        public Node getPriority() {
          return this;
        }

        @Override
        public boolean isEmpty() {
          return false;
        }

        @Override
        public boolean hasChild(ChildKey childKey) {
          return false;
        }

        @Override
        public Node getImmediateChild(ChildKey name) {
          if (name.isPriorityChildName()) {
            return getPriority();
          } else {
            return EmptyNode.Empty();
          }
        }

        @Override
        public String toString() {
          return "<Max Node>";
        }
      };

  boolean isLeafNode();

  Node getPriority();

  Node getChild(Path path);

  Node getImmediateChild(ChildKey name);

  Node updateImmediateChild(ChildKey name, Node node);

  ChildKey getPredecessorChildKey(ChildKey childKey);

  ChildKey getSuccessorChildKey(ChildKey childKey);

  Node updateChild(Path path, Node node);

  Node updatePriority(Node priority);

  boolean hasChild(ChildKey name);

  boolean isEmpty();

  int getChildCount();

  Object getValue();

  Object getValue(boolean useExportFormat);

  String getHash();

  String getHashRepresentation(HashVersion version);

  Iterator<NamedNode> reverseIterator();

  /** */
  enum HashVersion {
    // V1 is the initial hashing schema used by Firebase Database
    V1,
    // V2 escapes and quotes strings and is used by compound hashing
    V2
  }
}
