package com.google.firebase.database;

import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class InfoTestIT {

  @After
  public void tearDown() {
    TestHelpers.failOnFirstUncaughtException();
  }

  private DatabaseReference getRootNode() throws DatabaseException {
    return TestHelpers.getRandomNode().getRoot();
  }

  @Test
  public void canGetAReferenceToDotInfoNodes() throws DatabaseException {
    DatabaseReference root = getRootNode();

    assertEquals(TestConstants.TEST_NAMESPACE + "/.info", root.child(".info").toString());
    assertEquals(TestConstants.TEST_NAMESPACE + "/.info/foo", root.child(".info/foo").toString());

    DatabaseConfig ctx = TestHelpers.getContext(0);
    DatabaseReference ref = new DatabaseReference(TestConstants.TEST_NAMESPACE + "/.info", ctx);
    assertEquals(TestConstants.TEST_NAMESPACE + "/.info", ref.toString());
    ref = new DatabaseReference(TestConstants.TEST_NAMESPACE + "/.info/foo", ctx);
    assertEquals(TestConstants.TEST_NAMESPACE + "/.info/foo", ref.toString());
  }

  @Test
  public void cantWriteToDotInfo() throws DatabaseException {
    DatabaseReference ref = getRootNode().child(".info");

    try {
      ref.setValue("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.setValue("hi", 5);
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.setPriority("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.runTransaction(
          new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
              fail("Should not get called");
              return null;
            }

            @Override
            public void onComplete(
                DatabaseError error, boolean committed, DataSnapshot currentData) {
              fail("Should not get called");
            }
          });
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.removeValue();
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }

    try {
      ref.child("test").setValue("hi");
      fail("Should not be allowed");
    } catch (DatabaseException e) {
      // No-op, expected
    }
  }

  @Test
  public void canWatchInfoConnected()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();

    assertTrue(snap.getValue() instanceof Boolean);
  }

  @Test
  public void testManualConnectionManagementWorks()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref =
        new DatabaseReference(TestConstants.TEST_NAMESPACE, TestHelpers.newTestConfig());
    DatabaseReference refAlt =
        new DatabaseReference(TestConstants.TEST_ALT_NAMESPACE, TestHelpers.newTestConfig());

    // Wait until we're connected to both Databases
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();
    ReadFuture.untilEquals(refAlt.child(".info/connected"), true).timedGet();

    ref.getDatabase().goOffline();
    refAlt.getDatabase().goOffline();

    // Ensure we're disconnected from both Databases
    DataSnapshot snap =
        new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();
    assertFalse((Boolean) snap.getValue());

    DataSnapshot snapAlt =
        new ReadFuture(refAlt.child(".info/connected")).timedGet().get(0).getSnapshot();
    assertFalse((Boolean) snapAlt.getValue());

    // Ensure that we don't automatically reconnect upon new DatabaseReference creation
    DatabaseReference refDup = ref.getDatabase().getReference();

    try {
      ReadFuture.untilEquals(refDup.child(".info/connected"), true)
          .timedGet(1500, TimeUnit.MILLISECONDS);
      assert (false); // We should never get here!
    } catch (TimeoutException e) { //
    }

    ref.getDatabase().goOnline();
    refAlt.getDatabase().goOnline();

    // Ensure we're reconnected to both Databases
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();
    ReadFuture.untilEquals(refAlt.child(".info/connected"), true).timedGet();
  }

  @Test
  public void dotInfoConnectedGoesToFalseWhenDisconnected()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    // Wait until we're connected
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();

    DatabaseConfig ctx = TestHelpers.getContext(0);
    RepoManager.interrupt(ctx);

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();
    assertFalse((Boolean) snap.getValue());
    RepoManager.resume(ctx);
  }

  @Test
  public void dotInfoServerTimeOffset()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = getRootNode();

    DataSnapshot snap =
        new ReadFuture(ref.child(".info/serverTimeOffset")).timedGet().get(0).getSnapshot();
    assertTrue(snap.getValue() instanceof Long);
  }
}
