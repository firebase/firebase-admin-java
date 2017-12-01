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

package com.google.firebase.database.connection;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.connection.Connection.Delegate;
import com.google.firebase.database.connection.Connection.DisconnectReason;
import com.google.firebase.database.connection.Connection.WebsocketConnectionFactory;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockito.Mockito;

public class ConnectionTest {

  private static final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor();

  @AfterClass
  public static void tearDownClass() {
    executor.shutdownNow();
  }

  @Test
  public void testOpen() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();
    Mockito.verify(connFactory.wsConn, Mockito.times(1)).open();
  }

  @Test
  public void testClose() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();
    connFactory.connection.close();

    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
  }

  @Test
  public void testCloseWithReason() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();
    connFactory.connection.close(DisconnectReason.SERVER_RESET);

    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(
        DisconnectReason.SERVER_RESET);
  }

  @Test
  public void testSendRequest() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    // should not be sent, since not connected
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    connFactory.connection.sendRequest(data, true);
    Mockito.verify(connFactory.wsConn, Mockito.never()).send(Mockito.<String, Object>anyMap());

    Map<String, Object> handshake = ImmutableMap.<String, Object>of(
        "t", "c",
        "d", ImmutableMap.of("t", "h", "d", ImmutableMap.of(
            "ts", System.currentTimeMillis())));
    connFactory.connection.onMessage(handshake);

    Map<String, Object> expected = ImmutableMap.<String, Object>of("t", "d", "d", data);
    connFactory.connection.sendRequest(data, true);
    connFactory.connection.sendRequest(data, false);
    Mockito.verify(connFactory.wsConn, Mockito.times(2)).send(expected);
  }

  @Test
  public void testOnDataMessage() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    Map<String, Object> incoming = ImmutableMap.<String, Object>of(
        "t", "d",
        "d", data);
    connFactory.connection.onMessage(incoming);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDataMessage(data);
  }

  @Test
  public void testOnDataMessageParseError() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    Mockito.doThrow(ClassCastException.class).when(connFactory.delegate).onDataMessage(data);

    Map<String, Object> incoming = ImmutableMap.<String, Object>of(
        "t", "d",
        "d", data);
    connFactory.connection.onMessage(incoming);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
  }

  @Test
  public void testOnInvalidMessage() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    Map<String, Object> incoming = ImmutableMap.<String, Object>of("d", data);
    connFactory.connection.onMessage(incoming);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
  }

  @Test
  public void testOnShutdown() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    Map<String, Object> command = ImmutableMap.<String, Object>of(
        "t", "c",
        "d", ImmutableMap.of("t", "s", "d", "shutdown_reason"));
    connFactory.connection.onMessage(command);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onKill("shutdown_reason");
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
  }

  @Test
  public void testOnReset() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    Map<String, Object> command = ImmutableMap.<String, Object>of(
        "t", "c",
        "d", ImmutableMap.of("t", "r", "d", "reset_host"));
    connFactory.connection.onMessage(command);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onCacheHost("reset_host");
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(
        DisconnectReason.SERVER_RESET);
    Mockito.verify(connFactory.wsConn, Mockito.times(1)).close();
  }

  @Test
  public void testOnDisconnect() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    connFactory.connection.onDisconnect(true);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
    Mockito.verify(connFactory.wsConn, Mockito.never()).close();
  }

  @Test
  public void testOnDisconnectNeverConnected() {
    MockConnectionFactory connFactory = new MockConnectionFactory();
    connFactory.connection.open();

    connFactory.connection.onDisconnect(false);
    Mockito.verify(connFactory.delegate, Mockito.times(1)).onDisconnect(DisconnectReason.OTHER);
    Mockito.verify(connFactory.wsConn, Mockito.never()).close();
  }

  private static class MockConnectionFactory implements WebsocketConnectionFactory {

    private final Delegate delegate = Mockito.mock(Delegate.class);
    private final WebsocketConnection wsConn = Mockito.mock(WebsocketConnection.class);

    private final Connection connection;

    MockConnectionFactory() {
      this.connection = new Connection(
          TestHelpers.newConnectionContext(executor),
          Mockito.mock(HostInfo.class),
          delegate,
          this);
    }

    @Override
    public WebsocketConnection newConnection(WebsocketConnection.Delegate delegate) {
      return wsConn;
    }
  }

}
