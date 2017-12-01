package com.google.firebase.database.connection;

import static com.google.firebase.database.TestHelpers.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.TestHelpers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import org.junit.AfterClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class PersistentConnectionTest {

  private static final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor();

  @AfterClass
  public static void tearDownClass() {
    executor.shutdownNow();
  }

  @Test
  public void testOnReady() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onServerInfoUpdate(
        Mockito.<String, Object>anyMap());
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onConnect();
    assertEquals(2, connFactory.outgoing.size());
    assertEquals("s", connFactory.outgoing.get(0).getAction());
    assertEquals("gauth", connFactory.outgoing.get(1).getAction());
    assertTrue(connFactory.outgoing.get(1).sensitive);
  }

  @Test
  public void testAuthSuccess() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 1, "b", ImmutableMap.of("s", "ok")));
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onAuthStatus(true);
  }

  @Test
  public void testAuthFailure() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "r", 1, "b", ImmutableMap.of("s", "not_ok")));
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onAuthStatus(false);
  }

  @Test
  public void testUpgradeAuth() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.refreshAuthToken("new-token");
    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "r", 1, "b", ImmutableMap.of("s", "not_ok")));
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("auth", connFactory.outgoing.get(2).getAction());
  }

  @Test
  public void testUnauth() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.refreshAuthToken(null);
    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "r", 1, "b", ImmutableMap.of("s", "not_ok")));
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("unauth", connFactory.outgoing.get(2).getAction());
  }

  @Test
  public void testOnDataMessage() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "d", "b", ImmutableMap.of("p", "foo", "d", "test")));
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDataUpdate(
        ImmutableList.of("foo"), "test", false, null);

    List<Map<String, Object>> ranges = ImmutableList.<Map<String, Object>>of(
        ImmutableMap.<String, Object>of("s", "start", "e", "end", "m", "data"));
    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "rm", "b", ImmutableMap.of("p", "foo", "d", ranges)));
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onRangeMergeUpdate(
        Mockito.<String>anyList(), Mockito.<RangeMerge>anyList(), Mockito.nullable(Long.class));

    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "ac", "b", ImmutableMap.of("s", "status", "d", "reason")));
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onAuthStatus(false);
  }

  @Test
  public void testListen() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    ListenHashProvider hash = Mockito.mock(ListenHashProvider.class);
    Mockito.when(hash.getSimpleHash()).thenReturn("simpleHash");

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.listen(
        ImmutableList.of("listen"), ImmutableMap.<String, Object>of(), hash, null, callback);
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("q", connFactory.outgoing.get(2).getAction());

    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "r", 2, "b", ImmutableMap.of("s", "ok", "d", ImmutableMap.of())));
    Mockito.verify(callback, Mockito.times(1)).onRequestResult(null, null);

    connFactory.persistentConn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "c", "b", ImmutableMap.of("p", "listen")));
    Mockito.verify(callback, Mockito.times(1)).onRequestResult("permission_denied", null);
  }

  @Test
  public void testUnlisten() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    ListenHashProvider hash = Mockito.mock(ListenHashProvider.class);
    Mockito.when(hash.getSimpleHash()).thenReturn("simpleHash");

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.listen(
        ImmutableList.of("listen"), ImmutableMap.<String, Object>of(), hash, null, callback);
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("q", connFactory.outgoing.get(2).getAction());

    connFactory.persistentConn.unlisten(
        ImmutableList.of("listen"), ImmutableMap.<String, Object>of());
    assertEquals(4, connFactory.outgoing.size());
    assertEquals("n", connFactory.outgoing.get(3).getAction());
  }

  @Test
  public void testPut() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.put(ImmutableList.of("put"), "testData", callback);
    assertEquals(2, connFactory.outgoing.size());

    // Write should not be sent until auth is successful
    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 1, "b", ImmutableMap.of("s", "ok")));
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("p", connFactory.outgoing.get(2).getAction());

    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 2, "b", ImmutableMap.of("s", "ok")));
    Mockito.verify(callback, Mockito.times(1)).onRequestResult(null, null);
  }

  @Test
  public void testOnDisconnectPut() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.onDisconnectPut(ImmutableList.of("put"), "testData", callback);
    assertEquals(2, connFactory.outgoing.size());

    // Write should not be sent until auth is successful
    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 1, "b", ImmutableMap.of("s", "ok")));
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("o", connFactory.outgoing.get(2).getAction());

    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 2, "b", ImmutableMap.of("s", "ok")));
    Mockito.verify(callback, Mockito.times(1)).onRequestResult(null, null);
  }

  @Test
  public void testOnDisconnectMerge() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 1, "b", ImmutableMap.of("s", "ok")));

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.onDisconnectMerge(ImmutableList.of("put"),
        ImmutableMap.<String, Object>of("key", "value"), callback);

    // Write should not be sent until auth us successful
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("om", connFactory.outgoing.get(2).getAction());
  }

  @Test
  public void testOnDisconnectCancel() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");
    connFactory.persistentConn.onDataMessage(
        ImmutableMap.<String, Object>of("r", 1, "b", ImmutableMap.of("s", "ok")));

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.onDisconnectCancel(ImmutableList.of("put"), callback);

    // Write should not be sent until auth us successful
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("oc", connFactory.outgoing.get(2).getAction());
  }

  @Test
  public void testOnKill() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onKill("some_reason");
    Mockito.verify(connFactory.conn, Mockito.times(1)).close();
  }

  @Test
  public void testShutdown() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.shutdown();
    Mockito.verify(connFactory.conn, Mockito.times(1)).close();
  }

  @Test
  public void testOnDisconnect() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onDisconnect(Connection.DisconnectReason.OTHER);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect();
  }

  @Test
  public void testPurgeWrites() throws InterruptedException {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.persistentConn.onReady(System.currentTimeMillis(), "last-session-id");

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    connFactory.persistentConn.put(ImmutableList.of("put"), "testData", callback);
    connFactory.persistentConn.onDisconnectPut(ImmutableList.of("put"), "testData", callback);
    assertEquals(2, connFactory.outgoing.size());

    // Write should not be sent until auth is successful
    connFactory.persistentConn.purgeOutstandingWrites();
    Mockito.verify(callback, Mockito.times(2)).onRequestResult("write_canceled", null);
  }

  private static class MockConnectionFactory implements PersistentConnectionImpl.ConnectionFactory {

    private final Connection conn = Mockito.mock(Connection.class);
    private final PersistentConnection.Delegate delegate = Mockito.mock(
        PersistentConnection.Delegate.class);
    private final List<OutgoingMessage> outgoing = new ArrayList<>();
    private final Semaphore connected = new Semaphore(0);

    private final PersistentConnectionImpl persistentConn;

    MockConnectionFactory() throws InterruptedException {
      Mockito.doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          outgoing.add(new OutgoingMessage(
              (Map) invocation.getArgument(0), (boolean) invocation.getArgument(1)));
          return null;
        }
      }).when(conn).sendRequest(Mockito.<String, Object>anyMap(), Mockito.anyBoolean());

      Mockito.doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
          connected.release();
          return null;
        }
      }).when(conn).open();

      persistentConn = new PersistentConnectionImpl(
          TestHelpers.newConnectionContext(executor), null, delegate, this);
      persistentConn.initialize();
      waitFor(connected);
    }

    @Override
    public Connection newConnection(PersistentConnectionImpl delegate) {
      return conn;
    }
  }

  private static class OutgoingMessage {
    private final Map payload;
    private final boolean sensitive;

    OutgoingMessage(Map payload, boolean sensitive) {
      this.payload = payload;
      this.sensitive = sensitive;
    }

    String getAction() {
      return (String) this.payload.get("a");
    }
  }
}
