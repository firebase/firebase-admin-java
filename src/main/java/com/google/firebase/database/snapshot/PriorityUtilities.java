package com.google.firebase.database.snapshot;

import com.google.firebase.database.DatabaseException;

/**
 * User: greg Date: 5/16/13 Time: 5:02 PM
 */
public class PriorityUtilities {

  public static Node NullPriority() {
    return EmptyNode.Empty();
  }

  public static boolean isValidPriority(Node priority) {
    return priority.getPriority().isEmpty()
        && (priority.isEmpty()
        || priority instanceof DoubleNode
        || priority instanceof StringNode
        || priority instanceof DeferredValueNode);
  }

  public static Node parsePriority(Object value) {
    Node priority = NodeUtilities.NodeFromJSON(value);
    if (priority instanceof LongNode) {
      priority =
          new DoubleNode(
              Double.valueOf((Long) priority.getValue()), PriorityUtilities.NullPriority());
    }
    if (!isValidPriority(priority)) {
      throw new DatabaseException(
          "Invalid Firebase Database priority (must be a string, double, ServerValue, or null)");
    }
    return priority;
  }
}
