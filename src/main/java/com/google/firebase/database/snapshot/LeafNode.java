package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.utilities.Utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * User: greg Date: 5/16/13 Time: 4:42 PM
 */
public abstract class LeafNode<T extends LeafNode> implements Node {

  protected final Node priority;
  private String lazyHash;

  LeafNode(Node priority) {
    this.priority = priority;
  }

  private static int compareLongDoubleNodes(LongNode longNode, DoubleNode doubleNode) {
    Double longDoubleValue = Double.valueOf((Long) longNode.getValue());
    return (longDoubleValue).compareTo((Double) doubleNode.getValue());
  }

  @Override
  public boolean hasChild(ChildKey childKey) {
    return false;
  }

  @Override
  public boolean isLeafNode() {
    return true;
  }

  @Override
  public Node getPriority() {
    return priority;
  }

  @Override
  public Node getChild(Path path) {
    if (path.isEmpty()) {
      return this;
    } else if (path.getFront().isPriorityChildName()) {
      return this.priority;
    } else {
      return EmptyNode.Empty();
    }
  }

  @Override
  public Node updateChild(Path path, Node node) {
    ChildKey front = path.getFront();
    if (front == null) {
      return node;
    } else if (node.isEmpty() && !front.isPriorityChildName()) {
      return this;
    } else {
      assert !path.getFront().isPriorityChildName() || path.size() == 1;
      return updateImmediateChild(front, EmptyNode.Empty().updateChild(path.popFront(), node));
    }
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int getChildCount() {
    return 0;
  }

  @Override
  public ChildKey getPredecessorChildKey(ChildKey childKey) {
    return null;
  }

  @Override
  public ChildKey getSuccessorChildKey(ChildKey childKey) {
    return null;
  }

  @Override
  public Node getImmediateChild(ChildKey name) {
    if (name.isPriorityChildName()) {
      return this.priority;
    } else {
      return EmptyNode.Empty();
    }
  }

  @Override
  public Object getValue(boolean useExportFormat) {
    if (!useExportFormat || priority.isEmpty()) {
      return getValue();
    } else {
      Map<String, Object> result = new HashMap<>();
      result.put(".value", getValue());
      result.put(".priority", priority.getValue());
      return result;
    }
  }

  @Override
  public Node updateImmediateChild(ChildKey name, Node node) {
    if (name.isPriorityChildName()) {
      return this.updatePriority(node);
    } else if (node.isEmpty()) {
      return this;
    } else {
      return EmptyNode.Empty().updateImmediateChild(name, node).updatePriority(this.priority);
    }
  }

  @Override
  public String getHash() {
    if (this.lazyHash == null) {
      this.lazyHash = Utilities.sha1HexDigest(getHashRepresentation(HashVersion.V1));
    }
    return this.lazyHash;
  }

  protected String getPriorityHash(HashVersion version) {
    switch (version) {
      case V1:
      case V2:
        if (priority.isEmpty()) {
          return "";
        } else {
          return "priority:" + priority.getHashRepresentation(version) + ":";
        }
      default:
        throw new IllegalArgumentException("Unknown hash version: " + version);
    }
  }

  protected abstract LeafType getLeafType();

  @Override
  public Iterator<NamedNode> iterator() {
    return Collections.<NamedNode>emptyList().iterator();
  }

  @Override
  public Iterator<NamedNode> reverseIterator() {
    return Collections.<NamedNode>emptyList().iterator();
  }

  @Override
  public int compareTo(Node other) {
    if (other.isEmpty()) {
      return 1;
    } else if (other instanceof ChildrenNode) {
      return -1;
    } else {
      assert other.isLeafNode() : "Node is not leaf node!";
      if (this instanceof LongNode && other instanceof DoubleNode) {
        return compareLongDoubleNodes((LongNode) this, (DoubleNode) other);
      } else if (this instanceof DoubleNode && other instanceof LongNode) {
        return -1 * compareLongDoubleNodes((LongNode) other, (DoubleNode) this);
      } else {
        return this.leafCompare((LeafNode<?>) other);
      }
    }
  }

  protected abstract int compareLeafValues(T other);

  protected int leafCompare(LeafNode<?> other) {
    LeafType thisLeafType = this.getLeafType();
    LeafType otherLeafType = other.getLeafType();
    if (thisLeafType.equals(otherLeafType)) {
      // leaf type is the same, so we can safely cast to the right type
      @SuppressWarnings("unchecked")
      int value = this.compareLeafValues((T) other);
      return value;
    } else {
      return thisLeafType.compareTo(otherLeafType);
    }
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    String str = getValue(true).toString();
    return str.length() <= 100 ? str : (str.substring(0, 100) + "...");
  }

  /** */
  protected enum LeafType {
    // The order here defines the ordering of leaf nodes
    DeferredValue,
    Boolean,
    Number,
    String
  }
}
