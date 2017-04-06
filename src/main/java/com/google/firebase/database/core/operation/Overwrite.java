package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;

public class Overwrite extends Operation {

  private final Node snapshot;

  public Overwrite(OperationSource source, Path path, Node snapshot) {
    super(OperationType.Overwrite, source, path);
    this.snapshot = snapshot;
  }

  public Node getSnapshot() {
    return this.snapshot;
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (this.path.isEmpty()) {
      return new Overwrite(
          this.source, Path.getEmptyPath(), this.snapshot.getImmediateChild(childKey));
    } else {
      return new Overwrite(this.source, this.path.popFront(), this.snapshot);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "Overwrite { path=%s, source=%s, snapshot=%s }", getPath(), getSource(), this.snapshot);
  }
}
