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
import static org.junit.Assert.assertTrue;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RandomOperationGenerator;
import com.google.firebase.database.core.SyncTree;
import com.google.firebase.database.core.Tag;
import com.google.firebase.database.core.operation.AckUserWrite;
import com.google.firebase.database.core.operation.ListenComplete;
import com.google.firebase.database.core.operation.Merge;
import com.google.firebase.database.core.operation.Operation;
import com.google.firebase.database.core.operation.Overwrite;
import com.google.firebase.database.core.utilities.TestClock;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RandomPersistenceTest {

  private static final int NUM_TESTS = 25;
  private static final int OPERATIONS_PER_TEST = 300;

  private static final double LISTEN_PROBABILITY = 0.1;
  private static final int MAX_LISTEN_DEPTH = 3;
  private long currentWriteId = 0;
  private long currentUnackedWriteId = 0;

  private static FirebaseApp testApp;

  @BeforeClass
  public static void setUpClass() throws IOException {
    testApp = FirebaseApp.initializeApp(
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl("https://admin-java-sdk.firebaseio.com")
            .build());
  }

  @AfterClass
  public static void tearDownClass() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private static Map<Path, Node> fromCompoundWrite(CompoundWrite write) {
    Map<Path, Node> map = new HashMap<>();
    for (Map.Entry<Path, Node> entry : write) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }

  private void applyOperation(SyncTree syncTree, Operation operation, Map<QuerySpec, Tag> tagMap) {
    if (operation.getSource().isTagged()) {
      Tag tag =
          tagMap.get(new QuerySpec(operation.getPath(), operation.getSource().getQueryParams()));
      assert tag != null;
      switch (operation.getType()) {
        case ListenComplete: {
          syncTree.applyTaggedListenComplete(tag);
          break;
        }
        case Overwrite: {
          Overwrite overwrite = (Overwrite) operation;
          syncTree.applyTaggedQueryOverwrite(operation.getPath(), overwrite.getSnapshot(), tag);
          break;
        }
        case Merge: {
          Merge merge = (Merge) operation;
          syncTree.applyTaggedQueryMerge(
              operation.getPath(), fromCompoundWrite(merge.getChildren()), tag);
          break;
        }
        default: {
          throw new IllegalArgumentException("Can't have tagged operation: " + operation);
        }
      }
    } else {
      switch (operation.getType()) {
        case ListenComplete: {
          syncTree.applyListenComplete(operation.getPath());
          break;
        }
        case Overwrite: {
          Overwrite overwrite = (Overwrite) operation;
          if (operation.getSource().isFromServer()) {
            syncTree.applyServerOverwrite(operation.getPath(), overwrite.getSnapshot());
          } else {
            syncTree.applyUserOverwrite(
                operation.getPath(),
                overwrite.getSnapshot(),
                overwrite.getSnapshot(),
                currentWriteId++, /*visible=*/
                true, /*persist=*/
                true);
          }
          break;
        }
        case Merge: {
          Merge merge = (Merge) operation;
          if (operation.getSource().isFromServer()) {
            syncTree.applyServerMerge(
                operation.getPath(), fromCompoundWrite(merge.getChildren()));
          } else {
            syncTree.applyUserMerge(
                operation.getPath(),
                merge.getChildren(),
                merge.getChildren(),
                currentWriteId++, /*persist=*/
                true);
          }
          break;
        }
        case AckUserWrite: {
          AckUserWrite userWrite = (AckUserWrite) operation;
          syncTree.ackUserWrite(
              currentUnackedWriteId++, userWrite.isRevert(), /*persist=*/ true, new TestClock());
          break;
        }
        default: {
          throw new IllegalArgumentException("Can't have tagged operation: " + operation);
        }
      }
    }
  }

  @Test
  public void randomOperations() {
    Boolean verbose = false; // set to true for debugging.
    for (int i = 0; i < NUM_TESTS; i++) {
      final RandomOperationGenerator generator = new RandomOperationGenerator();
      Random random = new Random(generator.getSeed());

      Set<QuerySpec> activeListens = new HashSet<>();
      Set<QuerySpec> completeListens = new HashSet<>();
      currentWriteId = 0;
      currentUnackedWriteId = 0;

      DatabaseConfig cfg = TestHelpers.newFrozenTestConfig(testApp);
      MockPersistenceStorageEngine storageEngine = new MockPersistenceStorageEngine();
      DefaultPersistenceManager manager =
          new DefaultPersistenceManager(storageEngine, CachePolicy.NONE);
      final HashMap<QuerySpec, Tag> tagMap = new HashMap<>();
      SyncTree syncTree =
          new SyncTree(manager, new SyncTree.ListenProvider() {
              @Override
              public void startListening(
                  QuerySpec query,
                  Tag tag,
                  ListenHashProvider hash,
                  SyncTree.CompletionListener onListenComplete) {
                generator.listen(query);
                if (tag != null) {
                  tagMap.put(query, tag);
                }
              }

              @Override
              public void stopListening(QuerySpec query, Tag tag) {
                // TODO: unlisten
              }
            });

      for (int j = 0; j < OPERATIONS_PER_TEST; j++) {
        if (activeListens.isEmpty() || random.nextDouble() < LISTEN_PROBABILITY) {
          Path path = generator.nextRandomPath(MAX_LISTEN_DEPTH);
          QueryParams params = generator.nextRandomParams();
          // TODO: removable event registrations
          // TODO: unlistens
          QuerySpec querySpec = new QuerySpec(path, params);
          activeListens.add(querySpec);
          syncTree.addEventRegistration(new TestEventRegistration(querySpec));
          if (verbose) {
            System.err.println("Adding listen for: " + path + " => " + params);
          }
        } else {
          Operation op = generator.nextOperation();
          if (verbose) {
            System.err.println("Applying operation: " + op);
          }
          if (op instanceof ListenComplete) {
            if (op.getSource().getQueryParams() != null) {
              completeListens.add(new QuerySpec(op.getPath(), op.getSource().getQueryParams()));
            } else {
              completeListens.add(QuerySpec.defaultQueryAtPath(op.getPath()));
            }
          }
          applyOperation(syncTree, op, tagMap);
        }

        List<TrackedQuery> trackedQueries = storageEngine.loadTrackedQueries();

        // Check that active listens match tracked queries
        for (QuerySpec entry : completeListens) {
          Path path = entry.getPath();
          QueryParams params = entry.getParams();
          boolean found = false;
          for (TrackedQuery trackedQuery : trackedQueries) {
            if (trackedQuery.querySpec.equals(entry)) {
              assertTrue(trackedQuery.complete);
              found = true;
              break;
            }
          }
          assertTrue("Active listen must be in tracked queries", found);
          if (params.loadsAllData()) {
            Node persistenceNode = storageEngine.getCurrentNode(path);
            Node serverNode = generator.getCurrentServerNode(path);
            assertEquals("Node persisted should match server code", serverNode, persistenceNode);
          }
        }
      }
    }
  }

  private static class TestEventRegistration extends EventRegistration {

    private QuerySpec query;

    TestEventRegistration(@NotNull QuerySpec query) {
      this.query = query;
    }

    @Override
    public boolean respondsTo(Event.EventType eventType) {
      return false;
    }

    @Override
    public DataEvent createEvent(Change change, QuerySpec query) {
      // no-op
      return null;
    }

    @Override
    public void fireEvent(DataEvent dataEvent) {
      // no-op
    }

    @Override
    public void fireCancelEvent(DatabaseError error) {
      // no-op
    }

    @Override
    public EventRegistration clone(QuerySpec newQuery) {
      return new TestEventRegistration(newQuery);
    }

    @Override
    public boolean isSameListener(EventRegistration other) {
      return other == this;
    }

    @NotNull
    @Override
    public QuerySpec getQuerySpec() {
      return query;
    }
  }
}
