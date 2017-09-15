/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.core.persistence;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeepSyncedTestIT {

  private static long globalKeepSyncedTestCounter = 0;
  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setUpClass() throws IOException {
    masterApp = IntegrationTestUtils.ensureDefaultApp();
  }

  @Before
  public void prepareApp() {
    TestHelpers.wrapForErrorHandling(masterApp);
  }

  @After
  public void checkAndCleanupApp() {
    TestHelpers.assertAndUnwrapErrorHandlers(masterApp);
  }

  @Test
  public void testKeepSynced() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    ref.keepSynced(false);
    assertNotKeptSynced(ref);
  }

  // NOTE: This is not ideal behavior and should be fixed in a future release
  @Test
  public void testKeepSyncedAffectOnQueries() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    ref.keepSynced(true);
    Query query = ref.limitToFirst(5);
    query.keepSynced(true);
    assertIsKeptSynced(ref);

    ref.keepSynced(false);
    assertNotKeptSynced(ref);
    // currently, setting false on the default query affects all queries at that location
    assertNotKeptSynced(query);
  }

  @Test
  public void testMultipleKeepSynced() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    try {
      ref.keepSynced(true);
      ref.keepSynced(true);
      ref.keepSynced(true);
      assertIsKeptSynced(ref);

      // If it were balanced, this would not be enough
      ref.keepSynced(false);
      ref.keepSynced(false);
      assertNotKeptSynced(ref);

      // If it were balanced, this would not be enough
      ref.keepSynced(true);
      assertIsKeptSynced(ref);
    } finally {
      // cleanup
      ref.keepSynced(false);
    }
  }

  // NOTE: No RemoveAllListenersDoesNotAffectKeepSynced test, since JVM client doesn't have
  // removeAllListeners...

  @Test
  public void testRemoveSingleListener() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ref.keepSynced(true);
    try {
      assertIsKeptSynced(ref);

      // This will add and remove a listener.
      new ReadFuture(
          ref,
          new ReadFuture.CompletionCondition() {
            @Override
            public boolean isComplete(List<EventRecord> events) {
              return true;
            }
          })
          .timedGet();

      assertIsKeptSynced(ref);
    } finally {
      // cleanup
      ref.keepSynced(false);
    }
  }

  @Test
  public void testKeepSyncedWithExistingListener() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);

    ReadFuture readFuture;
    ref.keepSynced(true);
    try {
      assertIsKeptSynced(ref);

      readFuture =
          new ReadFuture(ref, new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                return events.get(events.size() - 1).getSnapshot().getValue().equals("done");
              }
            });
    } finally {
      // cleanup
      ref.keepSynced(false);
    }

    // Should trigger our listener.
    ref.setValueAsync("done");
    readFuture.timedGet();
  }

  @Test
  public void testDifferentIndependentQueries() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    Query query1 = ref.limitToFirst(1);
    Query query2 = ref.limitToFirst(2);

    query1.keepSynced(true);
    assertIsKeptSynced(query1);
    assertNotKeptSynced(query2);

    query2.keepSynced(true);
    assertIsKeptSynced(query1);
    assertIsKeptSynced(query2);

    query1.keepSynced(false);
    assertIsKeptSynced(query2);
    assertNotKeptSynced(query1);

    query2.keepSynced(false);
    assertNotKeptSynced(query1);
    assertNotKeptSynced(query2);
  }

  @Test
  public void testKeptSyncedChild() throws Exception {
    DatabaseReference ref = IntegrationTestUtils.getRandomNode(masterApp);
    DatabaseReference child = ref.child("random-child");

    ref.keepSynced(true);
    try {
      assertIsKeptSynced(child);
    } finally {
      // cleanup
      ref.keepSynced(false);
    }
  }

  @Test
  public void testKeptSyncedRoot() throws Exception {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference();

    ref.keepSynced(true);
    try {
      assertIsKeptSynced(ref);
    } finally {
      // cleanup
      ref.keepSynced(false);
    }
  }

  private void assertIsKeptSynced(Query query) throws Exception {
    DatabaseReference ref = query.getRef();

    // First set a unique value to the value of a child.
    long counter = globalKeepSyncedTestCounter++;
    final Map<String, Object> value = ImmutableMap.<String, Object>of("child", counter);
    new WriteFuture(ref, value).timedGet();

    // Next go offline, if it's kept synced we should have kept the value.
    // After going offline no way to get the value except from cache.
    ref.getDatabase().goOffline();

    try {
      new ReadFuture(
          query,
          new ReadFuture.CompletionCondition() {
            @Override
            public boolean isComplete(List<EventRecord> events) {
              assertEquals(1, events.size());
              assertEquals(value, events.get(0).getSnapshot().getValue());
              return true;
            }
          })
          .timedGet();
    } finally {
      // All good, go back online
      ref.getDatabase().goOnline();
    }
  }

  private void assertNotKeptSynced(Query query) throws Exception {
    DatabaseReference ref = query.getRef();

    // First set a unique value to the value of a child.
    long current = globalKeepSyncedTestCounter++;
    final Map<String, Object> oldValue = ImmutableMap.<String, Object>of("child", current);

    long next = globalKeepSyncedTestCounter++;
    final Map<String, Object> nextValue = ImmutableMap.<String, Object>of("child", next);

    new WriteFuture(ref, oldValue).timedGet();

    // Next go offline, if it's kept synced we should have kept the value and we'll get an even
    // with the *old* value.
    ref.getDatabase().goOffline();

    try {
      ReadFuture readFuture =
          new ReadFuture(query, new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                // We expect this to get called with the next value, not the old value.
                assertEquals(1, events.size());
                assertEquals(nextValue, events.get(0).getSnapshot().getValue());
                return true;
              }
            });

      // By now, if we had it synced we should have gotten an event with the wrong value
      // Write a new value so the value event listener will be triggered
      ref.setValueAsync(nextValue);
      readFuture.timedGet();
    } finally {
      // All good, go back online
      ref.getDatabase().goOnline();
    }
  }

  // TODO[offline]: Cancel listens for keep synced....
}
