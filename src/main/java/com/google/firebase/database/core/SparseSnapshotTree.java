package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.ChildrenNode;
import com.google.firebase.database.snapshot.Node;
import java.util.HashMap;
import java.util.Map;

/**
 * User: greg Date: 5/20/13 Time: 11:54 AM
 */
class SparseSnapshotTree {

  private Node value;
  private Map<ChildKey, SparseSnapshotTree> children;

  public SparseSnapshotTree() {
    this.value = null;
    this.children = null;
  }

  public interface SparseSnapshotTreeVisitor {

    void visitTree(Path prefixPath, Node tree);
  }

  public interface SparseSnapshotChildVisitor {

    void visitChild(ChildKey key, SparseSnapshotTree tree);
  }

  public void remember(Path path, Node data) {
    if (path.isEmpty()) {
      value = data;
      children = null;
    } else if (value != null) {
      value = value.updateChild(path, data);
    } else {
      if (children == null) {
        children = new HashMap<>();
      }

      ChildKey childKey = path.getFront();
      if (!children.containsKey(childKey)) {
        children.put(childKey, new SparseSnapshotTree());
      }

      SparseSnapshotTree child = children.get(childKey);
      child.remember(path.popFront(), data);
    }
  }

  public boolean forget(final Path path) {
    if (path.isEmpty()) {
      value = null;
      children = null;
      return true;
    } else {
      if (value != null) {
        if (value.isLeafNode()) {
          // non-empty path at leaf. The path leads to nowhere
          return false;
        } else {
          ChildrenNode childrenNode = (ChildrenNode) value;
          value = null;

          childrenNode.forEachChild(
              new ChildrenNode.ChildVisitor() {
                @Override
                public void visitChild(ChildKey name, Node child) {
                  remember(path.child(name), child);
                }
              });

          // We've cleared out the value and set the children. Call this method again to hit the
          // next case
          return forget(path);
        }
      } else if (children != null) {
        ChildKey childKey = path.getFront();
        Path childPath = path.popFront();

        if (children.containsKey(childKey)) {
          SparseSnapshotTree child = children.get(childKey);
          boolean safeToRemove = child.forget(childPath);
          if (safeToRemove) {
            children.remove(childKey);
          }
        }

        if (children.isEmpty()) {
          children = null;
          return true;
        } else {
          return false;
        }
      } else {
        return true;
      }
    }
  }

  public void forEachTree(final Path prefixPath, final SparseSnapshotTreeVisitor visitor) {
    if (value != null) {
      visitor.visitTree(prefixPath, value);
    } else {
      this.forEachChild(
          new SparseSnapshotChildVisitor() {
            @Override
            public void visitChild(ChildKey key, SparseSnapshotTree tree) {
              tree.forEachTree(prefixPath.child(key), visitor);
            }
          });
    }
  }

  public void forEachChild(SparseSnapshotChildVisitor visitor) {
    if (children != null) {
      for (Map.Entry<ChildKey, SparseSnapshotTree> entry : children.entrySet()) {
        visitor.visitChild(entry.getKey(), entry.getValue());
      }
    }
  }
}
