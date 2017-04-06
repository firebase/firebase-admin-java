package com.google.firebase.database.core.persistence;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.ImmutableTree;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.google.firebase.database.TestHelpers.ck;
import static org.junit.Assert.*;

public class PruneForestTest {

  @Test
  public void emptyDoesNotAffectAnyPaths() {
    PruneForest forest = new PruneForest();
    assertFalse(forest.affectsPath(Path.getEmptyPath()));
    assertFalse(forest.affectsPath(new Path("foo")));
  }

  @Test
  public void pruneAffectsPath() {
    PruneForest forest = new PruneForest();
    forest = forest.prune(new Path("foo/bar"));
    forest = forest.keep(new Path("foo/bar/baz"));
    assertTrue(forest.affectsPath(new Path("foo")));
    assertFalse(forest.affectsPath(new Path("baz")));
    assertFalse(forest.affectsPath(new Path("baz/bar")));
    assertTrue(forest.affectsPath(new Path("foo/bar")));
    assertTrue(forest.affectsPath(new Path("foo/bar/baz")));
    assertTrue(forest.affectsPath(new Path("foo/bar/qux")));
  }

  @Test
  public void prunesAnythingWorks() {
    PruneForest empty = new PruneForest();
    assertFalse(empty.prunesAnything());
    assertTrue(empty.prune(new Path("foo")).prunesAnything());

    assertFalse(empty.prune(new Path("foo/bar")).keep(new Path("foo")).prunesAnything());

    assertTrue(empty.prune(new Path("foo")).keep(new Path("foo/bar")).prunesAnything());
  }

  @Test
  public void keepUnderPruneWorks() {
    PruneForest forest = new PruneForest();
    forest = forest.prune(new Path("foo/bar"));
    forest = forest.keep(new Path("foo/bar/baz"));
    forest =
        forest.keepAll(new Path("foo/bar"), new HashSet<>(Arrays.asList(ck("qux"), ck("quu"))));
  }

  @Test
  public void pruneUnderKeepThrows() {
    PruneForest forest = new PruneForest();
    forest = forest.prune(new Path("foo"));
    forest = forest.keep(new Path("foo/bar"));
    try {
      forest = forest.prune(new Path("foo/bar/baz"));
      fail("Didn't throw!");
    } catch (Exception ignore) {
      //
    }
    try {
      forest =
          forest.pruneAll(new Path("foo/bar"), new HashSet<>(Arrays.asList(ck("qux"), ck("quu"))));
      fail("Didn't throw!");
    } catch (Exception ignore) {
      //
    }
  }

  @Test
  public void childKeepsPruneInfo() {
    PruneForest forest = new PruneForest();
    forest = forest.keep(new Path("foo/bar"));
    assertTrue(forest.child(ck("foo")).affectsPath(new Path("bar")));
    assertTrue(forest.child(ck("foo")).child(ck("bar")).affectsPath(new Path("")));
    assertTrue(forest.child(ck("foo")).child(ck("bar")).child(ck("baz")).affectsPath(new Path("")));

    forest = new PruneForest().prune(new Path("foo/bar"));
    assertTrue(forest.child(ck("foo")).affectsPath(new Path("bar")));
    assertTrue(forest.child(ck("foo")).child(ck("bar")).affectsPath(new Path("")));
    assertTrue(forest.child(ck("foo")).child(ck("bar")).child(ck("baz")).affectsPath(new Path("")));

    assertFalse(forest.child(ck("non-existent")).affectsPath(new Path("")));
  }

  @Test
  public void shouldPruneWorks() {
    PruneForest forest = new PruneForest();
    forest = forest.prune(new Path("foo"));
    forest = forest.keep(new Path("foo/bar/baz"));
    assertTrue(forest.shouldPruneUnkeptDescendants(new Path("foo")));
    assertTrue(forest.shouldPruneUnkeptDescendants(new Path("foo/bar")));
    assertFalse(forest.shouldPruneUnkeptDescendants(new Path("foo/bar/baz")));
    assertFalse(forest.shouldPruneUnkeptDescendants(new Path("qux")));
  }

  @Test
  public void foldKeepVisitsAllKeptNodes() {
    PruneForest forest = new PruneForest();
    forest = forest.prune(new Path("foo"));
    forest =
        forest.keepAll(new Path("foo/bar"), new HashSet<>(Arrays.asList(ck("qux"), ck("quu"))));
    forest = forest.keep(new Path("foo/baz"));
    forest = forest.keep(new Path("bar"));
    final HashSet<Path> actualPaths = new HashSet<>();
    forest.foldKeptNodes(
        null,
        new ImmutableTree.TreeVisitor<Void, Object>() {
          @Override
          public Object onNodeValue(Path relativePath, Void value, Object accum) {
            actualPaths.add(relativePath);
            return null;
          }
        });
    Set<Path> expected =
        new HashSet<>(
            Arrays.asList(
                new Path("foo/bar/qux"),
                new Path("foo/bar/quu"),
                new Path("foo/baz"),
                new Path("bar")));
    assertEquals(expected, actualPaths);
  }
}
