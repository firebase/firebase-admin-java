package com.google.firebase.database.collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class ImmutableSortedSetTest {

  private static  final List<String> expected = ImmutableList.of("bar", "baz", "foo");

  @Test
  public void testImmutableSortedSet() {
    ImmutableSortedSet<String> set = newSet();
    assertEquals(3, set.size());
    assertFalse(set.isEmpty());
    assertEquals("bar", set.getMinEntry());
    assertEquals("foo", set.getMaxEntry());
    assertEquals("bar", set.getPredecessorEntry("baz"));
    assertTrue(set.contains("foo"));
    assertFalse(set.contains("not found"));
    assertEquals(set.hashCode(), set.hashCode());
    assertEquals(set, set);

    ImmutableSortedSet<String> set2 = newSet();
    assertEquals(set.hashCode(), set2.hashCode());
    assertEquals(set, set2);

    List<String> original = new ArrayList<>();
    Collections.addAll(original, "abc", "mno", "pqr");
    set2 = new ImmutableSortedSet<>(original, Ordering.<String>natural());
    assertNotEquals(set.hashCode(), set2.hashCode());
    assertNotEquals(set, set2);
  }

  @Test
  public void testForwardIterators() {
    ImmutableSortedSet<String> set = newSet();
    Iterator<String> iterator = set.iterator();
    int i = 0;
    while (iterator.hasNext()) {
      assertEquals(expected.get(i++), iterator.next());
    }

    iterator = set.iteratorFrom("baz");
    i = 1;
    while (iterator.hasNext()) {
      assertEquals(expected.get(i++), iterator.next());
    }
  }

  @Test
  public void testReverseIterators() {
    ImmutableSortedSet<String> set = newSet();
    Iterator<String> iterator = set.reverseIterator();
    int i = 2;
    while (iterator.hasNext()) {
      assertEquals(expected.get(i--), iterator.next());
    }

    iterator = set.reverseIteratorFrom("baz");
    i = 1;
    while (iterator.hasNext()) {
      assertEquals(expected.get(i--), iterator.next());
    }
  }

  private ImmutableSortedSet<String> newSet() {
    List<String> original = new ArrayList<>();
    Collections.addAll(original, "foo", "bar", "baz");
    return new ImmutableSortedSet<>(original, Ordering.<String>natural());
  }

}
