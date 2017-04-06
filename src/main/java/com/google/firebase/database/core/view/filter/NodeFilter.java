package com.google.firebase.database.core.view.filter;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;

/**
 * NodeFilter is used to update nodes and complete children of nodes while applying queries on the
 * fly and keeping track of any child changes. This class does not track value changes as value
 * changes depend on more than just the node itself. Different kind of queries require different
 * kind of implementations of this interface.
 */
public interface NodeFilter {

  /**
   * Update a single complete child in the snap. If the child equals the old child in the snap, this
   * is a no-op. The method expects an indexed snap.
   */
  IndexedNode updateChild(
      IndexedNode node,
      ChildKey key,
      Node newChild,
      Path affectedPath,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator);

  /**
   * Update a node in full and output any resulting change from this complete update.
   */
  IndexedNode updateFullNode(
      IndexedNode oldSnap, IndexedNode newSnap, ChildChangeAccumulator optChangeAccumulator);

  /**
   * Update the priority of the root node
   */
  IndexedNode updatePriority(IndexedNode oldSnap, Node newPriority);

  /**
   * Returns true if children might be filtered due to query criteria
   */
  boolean filtersNodes();

  /**
   * Returns the index filter that this filter uses to get a NodeFilter that doesn't filter any
   * children.
   */
  NodeFilter getIndexedFilter();

  /**
   * Returns the index that this filter uses
   */
  Index getIndex();

  /**
   * Since updates to filtered nodes might require nodes to be pulled in from "outside" the node,
   * this interface can help to get complete children that can be pulled in. A class implementing
   * this interface takes potentially multiple sources (e.g. user writes, server data from other
   * views etc.) to try it's best to get a complete child that might be useful in pulling into the
   * view.
   */
  interface CompleteChildSource {

    Node getCompleteChild(ChildKey childKey);

    NamedNode getChildAfterChild(Index index, NamedNode child, boolean reverse);
  }
}
