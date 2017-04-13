package com.google.firebase.database.utilities.tuple;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;

/** User: greg Date: 5/17/13 Time: 3:19 PM */
public class NameAndPriority implements Comparable<NameAndPriority> {

  private ChildKey name;

  private Node priority;

  public NameAndPriority(ChildKey name, Node priority) {
    this.name = name;
    this.priority = priority;
  }

  public ChildKey getName() {
    return name;
  }

  public Node getPriority() {
    return priority;
  }

  @Override
  public int compareTo(NameAndPriority o) {
    return NodeUtilities.nameAndPriorityCompare(this.name, this.priority, o.name, o.priority);
  }
}
