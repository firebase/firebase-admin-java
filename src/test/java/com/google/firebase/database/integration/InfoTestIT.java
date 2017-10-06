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

package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.TestFailure;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.testing.IntegrationTestUtils;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.BeforeClass;
import org.junit.Test;

public class InfoTestIT {

  @BeforeClass
  public static void setUpClass() throws IOException {
    IntegrationTestUtils.ensureDefaultApp();
  }

  @Test
  public void testGetReferenceToInfoNodes() {
    DatabaseReference root = FirebaseDatabase.getInstance().getReference();
    String dbUrl = FirebaseApp.getInstance().getOptions().getDatabaseUrl();
    assertEquals(dbUrl + "/.info", root.child(".info").toString());
    assertEquals(dbUrl + "/.info/foo", root.child(".info/foo").toString());
  }

  @Test
  public void testInfoNodeSetValue() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
    try {
      ref.setValueAsync("hi");
      fail("Should not be allowed");
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoNodeSetValueWithPriority() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
    try {
      ref.setValueAsync("hi", 5);
      fail("Should not be allowed");
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoNodeSetPriority() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
    try {
      ref.setPriorityAsync("hi");
      fail("Should not be allowed");
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoNodeRunTransaction() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
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
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoNodeRemoveValue() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
    try {
      ref.removeValueAsync();
      fail("Should not be allowed");
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoNodeChildSetValue() {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference(".info");
    try {
      ref.child("test").setValueAsync("hi");
      fail("Should not be allowed");
    } catch (DatabaseException expected) {
      // No-op, expected
    }
  }

  @Test
  public void testInfoConnected() throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
    DataSnapshot snap = new ReadFuture(ref.child(".info/connected"))
        .timedGet().get(0).getSnapshot();
    assertTrue(snap.getValue() instanceof Boolean);
  }

  @Test
  public void testManualConnectionManagement()
      throws TestFailure, TimeoutException, InterruptedException {
    FirebaseApp app = IntegrationTestUtils.initApp("testManualConnectionManagement");
    DatabaseReference ref = FirebaseDatabase.getInstance(app).getReference();

    // Wait until we're connected to database
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();

    ref.getDatabase().goOffline();

    try {
      // Ensure we're disconnected
      DataSnapshot snap =
          new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();
      assertFalse((Boolean) snap.getValue());

      // Ensure that we don't automatically reconnect upon new DatabaseReference creation
      DatabaseReference refDup = FirebaseDatabase.getInstance(app).getReference();
      try {
        ReadFuture.untilEquals(refDup.child(".info/connected"), true)
            .timedGet(1500, TimeUnit.MILLISECONDS);
        fail("Did not timeout"); // We should never get here!
      } catch (TimeoutException e) { //
      }
    } finally {
      ref.getDatabase().goOnline();
      // Ensure we're reconnected
      ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();
    }
  }

  @Test
  public void testInfoConnectedOnDisconnect()
      throws TestFailure, TimeoutException, InterruptedException {
    FirebaseApp app = IntegrationTestUtils.initApp("testInfoConnectedOnDisconnect");
    DatabaseReference ref = FirebaseDatabase.getInstance(app).getReference();

    // Wait until we're connected
    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();

    DatabaseConfig ctx = TestHelpers.getDatabaseConfig(app);
    RepoManager.interrupt(ctx);
    try {
      DataSnapshot snap =
          new ReadFuture(ref.child(".info/connected")).timedGet().get(0).getSnapshot();
      assertFalse((Boolean) snap.getValue());
    } finally {
      RepoManager.resume(ctx);
    }
  }

  @Test
  public void testInfoServerTimeOffset()
      throws TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
    DataSnapshot snap =
        new ReadFuture(ref.child(".info/serverTimeOffset")).timedGet().get(0).getSnapshot();
    assertTrue(snap.getValue() instanceof Long);
  }
}
