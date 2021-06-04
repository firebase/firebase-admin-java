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

import static com.google.firebase.database.TestHelpers.childKeySet;
import static com.google.firebase.database.TestHelpers.defaultQueryAt;
import static com.google.firebase.database.TestHelpers.fromSingleQuotedString;
import static com.google.firebase.database.TestHelpers.newFrozenTestConfig;
import static com.google.firebase.database.TestHelpers.path;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PathIndex;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DefaultPersistenceManagerTest {

  private final QuerySpec defaultFooQuery = defaultQueryAt("foo");
  private final QuerySpec limit3FooQuery =
      new QuerySpec(new Path("foo"), QueryParams.DEFAULT_PARAMS.limitToLast(3));

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

  private PersistenceManager newTestPersistenceManager() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    engine.disableTransactionCheck = true;
    return new DefaultPersistenceManager(engine, CachePolicy.NONE);
  }

  @Test
  public void serverCacheFiltersResults1() {
    PersistenceManager manager = newTestPersistenceManager();

    manager.updateServerCache(defaultQueryAt("foo/bar"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/baz"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/quu/1"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/quu/2"), NodeFromJSON("4"));

    CacheNode cache = manager.serverCache(defaultQueryAt("foo"));
    assertFalse(cache.isFullyInitialized());
    assertEquals(EmptyNode.Empty(), cache.getNode());
  }

  @Test
  public void serverCacheFiltersResults2() {
    PersistenceManager manager = newTestPersistenceManager();

    manager.setQueryActive(limit3FooQuery);
    manager.updateServerCache(
        defaultQueryAt("foo"),
        NodeFromJSON(fromSingleQuotedString("{'a': 1, 'b': 2, 'c': 3, 'd': 4}")));
    manager.setTrackedQueryKeys(limit3FooQuery, childKeySet("a", "b"));
    CacheNode cache = manager.serverCache(defaultQueryAt("foo"));
    assertFalse(cache.isFullyInitialized());
    Node expected = NodeFromJSON(fromSingleQuotedString("{'a': 1, 'b': 2 }"));
    assertEquals(expected, cache.getNode());
  }

  @Test
  public void noLimitNonDefaultQueryIsTreatedAsDefaultQuery() {
    PersistenceManager manager = newTestPersistenceManager();

    manager.setQueryActive(defaultFooQuery);
    Node data = NodeFromJSON(fromSingleQuotedString("{'foo': 1, 'bar': 2 }"));
    manager.updateServerCache(defaultFooQuery, data);
    manager.setQueryComplete(defaultFooQuery);

    Index index = new PathIndex(path("index-key"));
    QuerySpec orderByQuery = new QuerySpec(path("foo"), new QueryParams().orderBy(index));
    CacheNode node = manager.serverCache(orderByQuery);
    assertTrue(node.isFullyInitialized());
    assertEquals(data, node.getNode());
    assertFalse(node.isFiltered());
    assertTrue(node.getIndexedNode().hasIndex(index));
  }

  @Test
  public void applyUserMergeUsesRelativePath() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    engine.disableTransactionCheck = true;

    Node initialData =
        NodeFromJSON(
            fromSingleQuotedString("{'foo': {'bar': 'bar-value', 'baz': " + "'baz-value'}}"));
    engine.overwriteServerCache(path(""), initialData);

    DefaultPersistenceManager manager =
        new DefaultPersistenceManager(engine, CachePolicy.NONE);

    CompoundWrite write =
        CompoundWrite.fromValue(fromSingleQuotedString("{'baz': 'new-baz', 'qux': 'qux'}"));
    manager.applyUserWriteToServerCache(path("foo"), write);

    Node expected =
        NodeFromJSON(
            fromSingleQuotedString(
                "{'foo': {'bar': 'bar-value', 'baz': 'new-baz', 'qux': 'qux'}}"));
    Node actual = engine.serverCache(path(""));
    assertEquals(expected, actual);
  }
}
