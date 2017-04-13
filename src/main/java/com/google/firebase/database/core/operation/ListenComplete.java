package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;

public class ListenComplete extends Operation {

  public ListenComplete(OperationSource source, Path path) {
    super(OperationType.ListenComplete, source, path);
    assert !source.isFromUser() : "Can't have a listen complete from a user source";
  }

  @Override
  public Operation operationForChild(ChildKey childKey) {
    if (this.path.isEmpty()) {
      return new ListenComplete(this.source, Path.getEmptyPath());
    } else {
      return new ListenComplete(this.source, this.path.popFront());
    }
  }

  @Override
  public String toString() {
    return String.format("ListenComplete { path=%s, source=%s }", getPath(), getSource());
  }
}
