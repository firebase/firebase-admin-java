package com.google.firebase.database.core.operation;

import static com.google.firebase.database.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.ImmutableTree;
import com.google.firebase.database.snapshot.ChildKey;

public class AckUserWrite extends Operation {

  private final boolean revert;
  // A tree containing true for each affected path.  Affected paths can't overlap.
  private final ImmutableTree<Boolean> affectedTree;

  public AckUserWrite(Path path, ImmutableTree<Boolean> affectedTree, boolean revert) {
    super(OperationType.AckUserWrite, OperationSource.USER, path);
    this.affectedTree = affectedTree;
    this.revert = revert;
  }

  public ImmutableTree<Boolean> getAffectedTree() {
    return this.affectedTree;
  }

  public boolean isRevert() {
    return this.revert;
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (!this.path.isEmpty()) {
      hardAssert(
          this.path.getFront().equals(childKey), "operationForChild called for unrelated " +
              "child.");
      return new AckUserWrite(this.path.popFront(), this.affectedTree, this.revert);
    } else if (this.affectedTree.getValue() != null) {
      hardAssert(
          this.affectedTree.getChildren().isEmpty(),
          "affectedTree should not have overlapping affected paths.");
      // All child locations are affected as well; just return same operation.
      return this;
    } else {
      ImmutableTree<Boolean> childTree = this.affectedTree.subtree(new Path(childKey));
      return new AckUserWrite(Path.getEmptyPath(), childTree, this.revert);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AckUserWrite { path=%s, revert=%s, affectedTree=%s }",
        getPath(), this.revert, this.affectedTree);
  }
}
