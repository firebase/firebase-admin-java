package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;

/**
 * A cache node only stores complete children. Additionally it holds a flag whether the node can be
 * considered fully initialized in the sense that we know at one point in time this represented a
 * valid state of the world, e.g. initialized with data from the server, or a complete overwrite by
 * the client. The filtered flag also tracks whether a node potentially had children removed due to
 * a filter.
 */
public class CacheNode {

  private final IndexedNode indexedNode;
  private final boolean fullyInitialized;
  private final boolean filtered;

  public CacheNode(IndexedNode node, boolean fullyInitialized, boolean filtered) {
    this.indexedNode = node;
    this.fullyInitialized = fullyInitialized;
    this.filtered = filtered;
  }

  /**
   * Returns whether this node was fully initialized with either server data or a complete overwrite
   * by the client
   */
  public boolean isFullyInitialized() {
    return this.fullyInitialized;
  }

  /**
   * Returns whether this node is potentially missing children due to a filter applied to the node
   */
  public boolean isFiltered() {
    return this.filtered;
  }

  public boolean isCompleteForPath(Path path) {
    if (path.isEmpty()) {
      return this.isFullyInitialized() && !this.filtered;
    } else {
      ChildKey childKey = path.getFront();
      return isCompleteForChild(childKey);
    }
  }

  public boolean isCompleteForChild(ChildKey key) {
    return (this.isFullyInitialized() && !this.filtered) || indexedNode.getNode().hasChild(key);
  }

  public Node getNode() {
    return this.indexedNode.getNode();
  }

  public IndexedNode getIndexedNode() {
    return this.indexedNode;
  }
}
