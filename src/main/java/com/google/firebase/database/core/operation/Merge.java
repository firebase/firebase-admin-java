package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;

public class Merge extends Operation {

  private final CompoundWrite children;

  public Merge(OperationSource source, Path path, CompoundWrite children) {
    super(OperationType.Merge, source, path);
    this.children = children;
  }

  public CompoundWrite getChildren() {
    return this.children;
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (this.path.isEmpty()) {
      CompoundWrite childTree = children.childCompoundWrite(new Path(childKey));
      if (childTree.isEmpty()) {
        // This child is unaffected.
        return null;
      } else if (childTree.rootWrite() != null) {
        // we have a set
        return new Overwrite(this.source, Path.getEmptyPath(), childTree.rootWrite());
      } else {
        return new Merge(this.source, Path.getEmptyPath(), childTree);
      }
    } else if (this.path.getFront().equals(childKey)) {
      return new Merge(this.source, this.path.popFront(), this.children);
    } else {
      // merge doesn't affect this path
      return null;
    }
  }

  @Override
  public String toString() {
    return String.format(
        "Merge { path=%s, source=%s, children=%s }", getPath(), getSource(), this.children);
  }
}
