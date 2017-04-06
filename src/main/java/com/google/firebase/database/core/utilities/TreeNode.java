package com.google.firebase.database.core.utilities;

import com.google.firebase.database.snapshot.ChildKey;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TreeNode<T> {

  public Map<ChildKey, TreeNode<T>> children;
  public T value;

  public TreeNode() {
    children = new HashMap<>();
  }

  String toString(String prefix) {
    String result = prefix + "<value>: " + value + "\n";
    if (children.isEmpty()) {
      return result + prefix + "<empty>";
    } else {
      Iterator<Map.Entry<ChildKey, TreeNode<T>>> iter = children.entrySet().iterator();
      while (iter.hasNext()) {
        Map.Entry<ChildKey, TreeNode<T>> entry = iter.next();
        result += prefix + entry.getKey() + ":\n" + entry.getValue().toString(prefix + "\t") + "\n";
      }
    }
    return result;
  }
}
