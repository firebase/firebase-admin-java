package com.google.firebase.database.connection;

import static com.google.firebase.database.TestHelpers.waitFor;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.core.ThreadInitializer;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.tubesock.ThreadConfig;
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

  private ConnectionContext newConnectionContext() {
    Logger logger = new DefaultLogger(Logger.Level.DEBUG, ImmutableList.<String>of());
    ConnectionAuthTokenProvider tokenProvider = new ConnectionAuthTokenProvider() {
      @Override
      public void getToken(boolean forceRefresh, GetTokenCallback callback) {
        callback.onSuccess("test-token");
      }
    };
    ThreadConfig config = new ThreadConfig(Executors.defaultThreadFactory(), ThreadInitializer
        .defaultInstance);
    return new ConnectionContext(logger, tokenProvider, executor, false, "testVersion",
        "testUserAgent", config);
  }

  @Test
  public void testOnReady() throws InterruptedException {
    PersistentConnection.Delegate delegate = Mockito.mock(PersistentConnection.Delegate.class);
    MockConnectionFactory connFactory = new MockConnectionFactory();
    PersistentConnectionImpl conn = new PersistentConnectionImpl(
        newConnectionContext(), null, delegate, connFactory);
    conn.initialize();
    waitFor(connFactory.connected);

    conn.onReady(System.currentTimeMillis(), "last-session-id");
    Mockito.verify(delegate, Mockito.times(1)).onConnect();
    assertEquals(2, connFactory.outgoing.size());
    assertEquals("s", connFactory.outgoing.get(0).getAction());
    assertEquals("auth", connFactory.outgoing.get(1).getAction());
  }

  @Test
  public void testOnDataMessage() throws InterruptedException {
    PersistentConnection.Delegate delegate = Mockito.mock(PersistentConnection.Delegate.class);
    MockConnectionFactory connFactory = new MockConnectionFactory();
    PersistentConnectionImpl conn = new PersistentConnectionImpl(
        newConnectionContext(), null, delegate, connFactory);
    conn.initialize();
    waitFor(connFactory.connected);

    conn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "d", "b", ImmutableMap.of("p", "foo", "d", "test")));
    Mockito.verify(delegate, Mockito.times(1)).onDataUpdate(
        ImmutableList.of("foo"), "test", false, null);

    List<Map<String, Object>> ranges = ImmutableList.<Map<String, Object>>of(
        ImmutableMap.<String, Object>of("s", "start", "e", "end", "m", "data"));
    conn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "rm", "b", ImmutableMap.of("p", "foo", "d", ranges)));
    Mockito.verify(delegate, Mockito.times(1)).onRangeMergeUpdate(
        Mockito.<String>anyList(), Mockito.<RangeMerge>anyList(), Mockito.nullable(Long.class));

    conn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "ac", "b", ImmutableMap.of("s", "status", "d", "reason")));
    Mockito.verify(delegate, Mockito.times(1)).onAuthStatus(false);
  }

  @Test
  public void testListen() throws InterruptedException {
    PersistentConnection.Delegate delegate = Mockito.mock(PersistentConnection.Delegate.class);
    MockConnectionFactory connFactory = new MockConnectionFactory();
    PersistentConnectionImpl conn = new PersistentConnectionImpl(
        newConnectionContext(), null, delegate, connFactory);
    conn.initialize();
    waitFor(connFactory.connected);

    conn.onReady(System.currentTimeMillis(), "last-session-id");
    ListenHashProvider hash = Mockito.mock(ListenHashProvider.class);
    Mockito.when(hash.getSimpleHash()).thenReturn("simpleHash");

    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    conn.listen(
        ImmutableList.of("listen"), ImmutableMap.<String, Object>of(), hash, null, callback);
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("q", connFactory.outgoing.get(2).getAction());

    conn.onDataMessage(ImmutableMap.<String, Object>of(
        "a", "c", "b", ImmutableMap.of("p", "listen")));
    Mockito.verify(callback, Mockito.times(1)).onRequestResult("permission_denied", null);
  }

  @Test
  public void testPut() throws InterruptedException {
    PersistentConnection.Delegate delegate = Mockito.mock(PersistentConnection.Delegate.class);
    MockConnectionFactory connFactory = new MockConnectionFactory();
    PersistentConnectionImpl conn = new PersistentConnectionImpl(
        newConnectionContext(), null, delegate, connFactory);
    conn.initialize();
    waitFor(connFactory.connected);

    conn.onReady(System.currentTimeMillis(), "last-session-id");
    RequestResultCallback callback = Mockito.mock(RequestResultCallback.class);
    conn.put(ImmutableList.of("put"), "testData", callback);
    assertEquals(3, connFactory.outgoing.size());
    assertEquals("p", connFactory.outgoing.get(2).getAction());
  }

  @Test
  public void testShutdown() throws InterruptedException {
    PersistentConnection.Delegate delegate = Mockito.mock(PersistentConnection.Delegate.class);
    MockConnectionFactory connFactory = new MockConnectionFactory();
    PersistentConnectionImpl conn = new PersistentConnectionImpl(
        newConnectionContext(), null, delegate, connFactory);
    conn.initialize();
    waitFor(connFactory.connected);

    conn.shutdown();
    Mockito.verify(connFactory.conn, Mockito.times(1)).close();
  }

  private static class MockConnectionFactory implements PersistentConnectionImpl.ConnectionFactory {

    private final Connection conn = Mockito.mock(Connection.class);

    private final List<OutgoingMessage> outgoing = new ArrayList<>();
    private final Semaphore connected = new Semaphore(0);

    MockConnectionFactory() {
      Mockito.doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          outgoing.add(new OutgoingMessage(
              (Map) invocation.getArgument(0), (boolean) invocation.getArgument(1)));
          return null;
        }
      }).when(conn).sendRequest(Mockito.<String, Object>anyMap(), Mockito.anyBoolean());
    }

    @Override
    public Connection newConnection(ConnectionContext context, HostInfo hostInfo, String
        cachedHost, Connection.Delegate delegate, String lastSessionId) {
      try {
        return conn;
      } finally {
        connected.release();
      }
    }
  }

  private static class OutgoingMessage {
    private final Map<String, Object> payload;
    private final boolean sensitive;

    OutgoingMessage(Map<String, Object> payload, boolean sensitive) {
      this.payload = payload;
      this.sensitive = sensitive;
    }

    String getAction() {
      return (String) this.payload.get("a");
    }
  }

}
