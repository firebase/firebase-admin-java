package com.google.firebase.database.core.operation;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;

public abstract class Operation {

  /** */
  public enum OperationType {
    Overwrite,
    Merge,
    AckUserWrite,
    ListenComplete
  }

  protected final OperationType type;
  protected final OperationSource source;
  protected final Path path;

  protected Operation(OperationType type, OperationSource source, Path path) {
    this.type = type;
    this.source = source;
    this.path = path;
  }

  public Path getPath() {
    return this.path;
  }

  public OperationSource getSource() {
    return this.source;
  }

  public OperationType getType() {
    return this.type;
  }

  public abstract Operation operationForChild(ChildKey childKey);
}
