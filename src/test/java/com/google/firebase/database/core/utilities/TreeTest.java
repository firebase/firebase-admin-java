package com.google.firebase.database.core.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import org.junit.Test;

public class TreeTest {

  @Test
  public void testDeletion() {
    /*
     * At one point, we had a concurrent modification exception in the Tree code, so just make sure
     * this doesn't throw a ConcurrentModification exception
     */
    Tree<String> root = new Tree<>();
    Path path = new Path("foo/bar");
    root.subTree(path).setValue("bar");
    root.subTree(path.getParent().child(ChildKey.fromString("baz"))).setValue("baz");
    Tree<String> intermediate = root.subTree(new Path("foo"));
    intermediate.forEachChild(
        new Tree.TreeVisitor<String>() {
          @Override
          public void visitTree(Tree<String> tree) {
            if (tree.getName().equals(ChildKey.fromString("baz"))) {
              tree.setValue(null);
            }
          }
        });
    String result = root.subTree(new Path("foo/bar")).getValue();
    assertEquals("bar", result);
    result = root.subTree(new Path("foo/baz")).getValue();
    assertNull(result);
  }
}
