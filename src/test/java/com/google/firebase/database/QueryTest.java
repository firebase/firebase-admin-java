package com.google.firebase.database;

import static com.google.firebase.database.TestHelpers.mockRepo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;

import com.google.firebase.database.core.ChildEventRegistration;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.ValueEventRegistration;
import com.google.firebase.database.snapshot.KeyIndex;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityIndex;
import com.google.firebase.database.snapshot.ValueIndex;
import org.junit.Test;
import org.mockito.Mockito;

public class QueryTest {

  private static final Path path = new Path("foo");

  @Test
  public void testQuery() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    assertSame(query.getPath(), path);
    assertSame(repo, query.getRepo());
    assertFalse(query.params.hasStart());
    assertFalse(query.params.hasEnd());
    assertFalse(query.params.hasLimit());
    assertFalse(query.params.hasAnchoredLimit());

    DatabaseReference ref = query.getRef();
    assertEquals("foo", ref.getKey());
  }

  @Test
  public void testStartAt() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path).orderByValue();
    assertSame(query.getPath(), path);

    Query startAt = query.startAt(1);
    assertNotSame(startAt, query);
    assertSame(startAt.getPath(), path);
    assertTrue(startAt.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON(1.0), startAt.params.getIndexStartValue());
    assertFalse(startAt.params.hasEnd());
    assertFalse(startAt.params.hasLimit());
    assertFalse(startAt.params.hasAnchoredLimit());

    startAt = query.startAt("foo");
    assertNotSame(startAt, query);
    assertSame(startAt.getPath(), path);
    assertTrue(startAt.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON("foo"), startAt.params.getIndexStartValue());
    assertFalse(startAt.params.hasEnd());
    assertFalse(startAt.params.hasLimit());
    assertFalse(startAt.params.hasAnchoredLimit());

    startAt = query.startAt(true);
    assertNotSame(startAt, query);
    assertSame(startAt.getPath(), path);
    assertTrue(startAt.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON(true), startAt.params.getIndexStartValue());
    assertFalse(startAt.params.hasEnd());
    assertFalse(startAt.params.hasLimit());
    assertFalse(startAt.params.hasAnchoredLimit());

    try {
      startAt.startAt(10);
      fail("No error thrown for multiple startAt() calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testEndAt() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path).orderByValue();
    assertSame(query.getPath(), path);

    Query endAt = query.endAt(1);
    assertNotSame(endAt, query);
    assertSame(endAt.getPath(), path);
    assertFalse(endAt.params.hasStart());
    assertTrue(endAt.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON(1.0), endAt.params.getIndexEndValue());
    assertFalse(endAt.params.hasLimit());
    assertFalse(endAt.params.hasAnchoredLimit());

    endAt = query.endAt("foo");
    assertNotSame(endAt, query);
    assertSame(endAt.getPath(), path);
    assertFalse(endAt.params.hasStart());
    assertTrue(endAt.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON("foo"), endAt.params.getIndexEndValue());
    assertFalse(endAt.params.hasLimit());
    assertFalse(endAt.params.hasAnchoredLimit());

    endAt = query.endAt(true);
    assertNotSame(endAt, query);
    assertSame(endAt.getPath(), path);
    assertFalse(endAt.params.hasStart());
    assertTrue(endAt.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON(true), endAt.params.getIndexEndValue());
    assertFalse(endAt.params.hasLimit());
    assertFalse(endAt.params.hasAnchoredLimit());

    try {
      endAt.endAt(10);
      fail("No error thrown for multiple endAt() calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testEqualTo() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path).orderByValue();
    assertSame(query.getPath(), path);

    Query equalTo = query.equalTo(1);
    assertNotSame(equalTo, query);
    assertSame(equalTo.getPath(), path);
    assertTrue(equalTo.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON(1.0), equalTo.params.getIndexStartValue());
    assertTrue(equalTo.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON(1.0), equalTo.params.getIndexEndValue());
    assertFalse(equalTo.params.hasLimit());
    assertFalse(equalTo.params.hasAnchoredLimit());

    equalTo = query.equalTo("foo");
    assertNotSame(equalTo, query);
    assertSame(equalTo.getPath(), path);
    assertTrue(equalTo.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON("foo"), equalTo.params.getIndexStartValue());
    assertTrue(equalTo.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON("foo"), equalTo.params.getIndexEndValue());
    assertFalse(equalTo.params.hasLimit());
    assertFalse(equalTo.params.hasAnchoredLimit());

    equalTo = query.equalTo(true);
    assertNotSame(equalTo, query);
    assertSame(equalTo.getPath(), path);
    assertTrue(equalTo.params.hasStart());
    assertEquals(NodeUtilities.NodeFromJSON(true), equalTo.params.getIndexStartValue());
    assertTrue(equalTo.params.hasEnd());
    assertEquals(NodeUtilities.NodeFromJSON(true), equalTo.params.getIndexEndValue());
    assertFalse(equalTo.params.hasLimit());
    assertFalse(equalTo.params.hasAnchoredLimit());

    try {
      equalTo.equalTo(10);
      fail("No error thrown for multiple equalTo() calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      equalTo.startAt(10);
      fail("No error thrown for invalid startAt() call");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      equalTo.endAt(10);
      fail("No error thrown for invalid endAt() call");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testLimitToFirst() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path).orderByValue();
    assertSame(query.getPath(), path);

    Query limitTo = query.limitToFirst(10);
    assertNotSame(limitTo, query);
    assertSame(limitTo.getPath(), path);
    assertFalse(limitTo.params.hasStart());
    assertFalse(limitTo.params.hasEnd());
    assertTrue(limitTo.params.hasLimit());
    assertTrue(limitTo.params.hasAnchoredLimit());
    assertEquals(10, limitTo.params.getLimit());

    try {
      limitTo.limitToFirst(10);
      fail("No error thrown for multiple limitToFirst() calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      query.limitToFirst(0);
      fail("No error thrown for 0 limit");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      query.limitToFirst(-1);
      fail("No error thrown for negative limit");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testLimitToLast() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path).orderByValue();
    assertSame(query.getPath(), path);

    Query limitTo = query.limitToLast(10);
    assertNotSame(limitTo, query);
    assertSame(limitTo.getPath(), path);
    assertFalse(limitTo.params.hasStart());
    assertFalse(limitTo.params.hasEnd());
    assertTrue(limitTo.params.hasLimit());
    assertTrue(limitTo.params.hasAnchoredLimit());
    assertEquals(10, limitTo.params.getLimit());

    try {
      limitTo.limitToLast(10);
      fail("No error thrown for multiple limitToLast() calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      query.limitToLast(0);
      fail("No error thrown for 0 limit");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      query.limitToLast(-1);
      fail("No error thrown for negative limit");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testOrderByChild() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);

    String[] invalidKeys = new String[]{
        "$key", ".key", "$value", ".value", "$priority", ".priority", ""
    };
    for (String key : invalidKeys) {
      try {
        query.orderByChild(key);
        fail("No error thrown for invalid child: " + key);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    try {
      query.orderByChild(null);
      fail("No error thrown for null child");
    } catch (NullPointerException expected) {
      // expected
    }

    Query orderBy = query.orderByChild("foo");
    assertNotSame(orderBy, query);
    assertEquals("foo", orderBy.params.getIndex().getQueryDefinition());

    try {
      orderBy.orderByChild("bar");
      fail("No error thrown for multiple orderBy calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      orderBy.orderByKey();
      fail("No error thrown for multiple orderBy calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      orderBy.orderByValue();
      fail("No error thrown for multiple orderBy calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      orderBy.orderByPriority();
      fail("No error thrown for multiple orderBy calls");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testOrderByPriority() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    assertTrue(query.orderByPriority().params.getIndex() instanceof PriorityIndex);
  }

  @Test
  public void testOrderByKey() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    assertTrue(query.orderByKey().params.getIndex() instanceof KeyIndex);
  }

  @Test
  public void testOrderByValue() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    assertTrue(query.orderByValue().params.getIndex() instanceof ValueIndex);
  }

  @Test
  public void testValueEventListener() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    ValueEventListener listener = new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    };

    query.addValueEventListener(listener);
    query.removeEventListener(listener);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(1))
        .addEventCallback(Mockito.any(ValueEventRegistration.class));
    Mockito.verify(repo, times(1))
        .removeEventCallback(Mockito.any(ValueEventRegistration.class));
  }

  @Test
  public void testSingleValueEventListener() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    try {
      query.addValueEventListener(null);
      fail("No error thrown for null event listener");
    } catch (NullPointerException expected) {
      // expected
    }
    try {
      query.removeEventListener((ValueEventListener) null);
      fail("No error thrown for null event listener");
    } catch (NullPointerException expected) {
      // expected
    }
    ValueEventListener listener = new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    };

    query.addListenerForSingleValueEvent(listener);
    Mockito.verify(repo, times(1))
        .scheduleNow(Mockito.any(Runnable.class));
    // Only add will be called in this test. Remove won't be called until the event fires.
    Mockito.verify(repo, times(1))
        .addEventCallback(Mockito.any(ValueEventRegistration.class));
  }

  @Test
  public void testChildEventListener() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    try {
      query.addChildEventListener(null);
      fail("No error thrown for null event listener");
    } catch (NullPointerException expected) {
      // expected
    }
    try {
      query.removeEventListener((ChildEventListener) null);
      fail("No error thrown for null event listener");
    } catch (NullPointerException expected) {
      // expected
    }
    ChildEventListener listener = new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    };

    query.addChildEventListener(listener);
    query.removeEventListener(listener);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(1))
        .addEventCallback(Mockito.any(ChildEventRegistration.class));
    Mockito.verify(repo, times(1))
        .removeEventCallback(Mockito.any(ChildEventRegistration.class));
  }

  @Test
  public void testKeepSynced() {
    Repo repo = mockRepo();
    Query query = new Query(repo, path);
    query.keepSynced(true);
    Mockito.verify(repo, times(1))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(1))
        .keepSynced(Mockito.eq(query.getSpec()), Mockito.eq(true));

    query = new Query(repo, new Path(".info/foo"));
    try {
      query.keepSynced(true);
      fail("No error thrown for keepSynced() call on .info node");
    } catch (DatabaseException expected) {
      // expected
    }
  }

}
