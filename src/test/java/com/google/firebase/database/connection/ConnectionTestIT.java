package com.google.firebase.database.connection;

import com.google.firebase.database.TestConstants;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.core.DatabaseConfig;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertFalse;

// TODO(mikelehen): Move this test to separate firebase-database-connection
// tests.
public class ConnectionTestIT {

  /**
   * @throws InterruptedException Test to see if we can get a sessionID from a Connection and pass
   *     it up to the Delegate
   */
  @Test
  public void testObtainSessionID() throws InterruptedException {
    final Semaphore valSemaphore = new Semaphore(0);
    Connection.Delegate del =
        new Connection.Delegate() {
          @Override
          public void onReady(long timestamp, String sessionId) {
            assertFalse("sessionId is null", sessionId == null);
            assertFalse("sessionId is empty", sessionId.isEmpty());
            valSemaphore.release();
          }

          @Override
          public void onDataMessage(Map<String, Object> message) {}

          @Override
          public void onDisconnect(Connection.DisconnectReason reason) {}

          @Override
          public void onKill(String reason) {}

          @Override
          public void onCacheHost(String s) {}
        };
    HostInfo info =
        new HostInfo(
            TestConstants.TEST_REPO + "." + TestConstants.TEST_SERVER,
            TestConstants.TEST_REPO, /*secure=*/
            false);
    DatabaseConfig config = TestHelpers.newFrozenTestConfig();
    Connection conn = new Connection(config.getConnectionContext(), info, null, del, null);
    conn.open();
    TestHelpers.waitFor(valSemaphore);
  }
}
