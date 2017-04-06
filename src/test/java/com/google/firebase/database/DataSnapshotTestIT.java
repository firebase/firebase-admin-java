package com.google.firebase.database;

import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;

import static org.junit.Assert.*;

public class DataSnapshotTestIT {

  @Before
  public void setUp() {
    TestHelpers.ensureAppInitialized();
  }

  private DataSnapshot snapFor(Object data, DatabaseReference ref) {
    Node node = NodeUtilities.NodeFromJSON(data);
    return new DataSnapshot(ref, IndexedNode.from(node));
  }

  @Test
  public void basicIterationWorks() {
    DatabaseReference ref = TestHelpers.getRandomNode();
    DataSnapshot snap1 = snapFor(null, ref);

    assertFalse(snap1.hasChildren());
    assertFalse(snap1.getChildren().iterator().hasNext());

    DataSnapshot snap2 = snapFor(1L, ref);
    assertFalse(snap2.hasChildren());
    assertFalse(snap2.getChildren().iterator().hasNext());

    DataSnapshot snap3 = snapFor(new MapBuilder().put("a", 1L).put("b", 2L).build(), ref);
    assertTrue(snap3.hasChildren());
    Iterator<DataSnapshot> iter = snap3.getChildren().iterator();
    assertTrue(iter.hasNext());

    String[] children = new String[] {null, null};
    int i = 0;
    for (DataSnapshot child : snap3.getChildren()) {
      children[i] = child.getKey();
      i++;
    }
    assertArrayEquals(children, new String[] {"a", "b"});
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void existsWorks() {
    DatabaseReference ref = TestHelpers.getRandomNode();
    DataSnapshot snap;

    snap = snapFor(new HashMap(), ref);
    assertFalse(snap.exists());

    snap = snapFor(new MapBuilder().put(".priority", 1).build(), ref);
    assertFalse(snap.exists());

    snap = snapFor(null, ref);
    assertFalse(snap.exists());

    snap = snapFor(true, ref);
    assertTrue(snap.exists());

    snap = snapFor(5, ref);
    assertTrue(snap.exists());

    snap = snapFor(new MapBuilder().put("x", 5).build(), ref);
    assertTrue(snap.exists());
  }
}
