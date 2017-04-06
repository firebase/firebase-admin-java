package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.Path;

import java.util.Comparator;

public abstract class Index implements Comparator<NamedNode> {

  public static Index fromQueryDefinition(String str) {
    if (str.equals(".value")) {
      return ValueIndex.getInstance();
    } else if (str.equals(".key")) {
      return KeyIndex.getInstance();
    } else if (str.equals(".priority")) {
      throw new IllegalStateException(
          "queryDefinition shouldn't ever be .priority since it's the default");
    } else {
      return new PathIndex(new Path(str));
    }
  }

  public abstract boolean isDefinedOn(Node a);

  public boolean indexedValueChanged(Node oldNode, Node newNode) {
    NamedNode oldWrapped = new NamedNode(ChildKey.getMinName(), oldNode);
    NamedNode newWrapped = new NamedNode(ChildKey.getMinName(), newNode);
    return this.compare(oldWrapped, newWrapped) != 0;
  }

  public abstract NamedNode makePost(ChildKey name, Node value);

  public NamedNode minPost() {
    return NamedNode.getMinNode();
  }

  public abstract NamedNode maxPost();

  public abstract String getQueryDefinition();

  public int compare(NamedNode one, NamedNode two, boolean reverse) {
    if (reverse) {
      return this.compare(two, one);
    } else {
      return this.compare(one, two);
    }
  }
}
