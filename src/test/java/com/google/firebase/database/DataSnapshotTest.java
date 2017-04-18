package com.google.firebase.database;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.testing.ServiceAccount;
import java.util.HashMap;
import java.util.Iterator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DataSnapshotTest {

  private static FirebaseApp testApp;
  private static DatabaseConfig config;

  @BeforeClass
  public static void setUpClass() {
    testApp = FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl("https://admin-java-sdk.firebaseio.com")
            .build());
    // Obtain a new DatabaseConfig instance for testing. Since we are not connecting to an
    // actual Firebase database, it is necessary to use a stand-in DatabaseConfig here.
    config = TestHelpers.newTestConfig(testApp);
  }

  @AfterClass
  public static void tearDownClass() throws InterruptedException {
    // Tear down and clean up the test DatabaseConfig.
    TestHelpers.interruptConfig(config);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  private DataSnapshot snapFor(Object data) {
    Node node = NodeUtilities.NodeFromJSON(data);
    DatabaseReference ref = new DatabaseReference("https://test.firebaseio.com", config);
    return new DataSnapshot(ref, IndexedNode.from(node));
  }

  @Test
  public void testBasicIteration() {
    DataSnapshot snap1 = snapFor(null);

    assertFalse(snap1.hasChildren());
    assertFalse(snap1.getChildren().iterator().hasNext());

    DataSnapshot snap2 = snapFor(1L);
    assertFalse(snap2.hasChildren());
    assertFalse(snap2.getChildren().iterator().hasNext());

    DataSnapshot snap3 = snapFor(MapBuilder.of("a", 1L, "b", 2L));
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
  public void testExists() {
    DataSnapshot snap;

    snap = snapFor(new HashMap<>());
    assertFalse(snap.exists());

    snap = snapFor(MapBuilder.of(".priority", 1));
    assertFalse(snap.exists());

    snap = snapFor(null);
    assertFalse(snap.exists());

    snap = snapFor(true);
    assertTrue(snap.exists());

    snap = snapFor(5);
    assertTrue(snap.exists());

    snap = snapFor(MapBuilder.of("x", 5));
    assertTrue(snap.exists());
  }
}
