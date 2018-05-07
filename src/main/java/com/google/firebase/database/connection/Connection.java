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

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Connection implements WebsocketConnection.Delegate {

  private static final String REQUEST_TYPE = "t";
  private static final String REQUEST_TYPE_DATA = "d";
  private static final String REQUEST_PAYLOAD = "d";
  private static final String SERVER_ENVELOPE_TYPE = "t";
  private static final String SERVER_DATA_MESSAGE = "d";
  private static final String SERVER_CONTROL_MESSAGE = "c";
  private static final String SERVER_ENVELOPE_DATA = "d";
  private static final String SERVER_CONTROL_MESSAGE_TYPE = "t";
  private static final String SERVER_CONTROL_MESSAGE_SHUTDOWN = "s";
  private static final String SERVER_CONTROL_MESSAGE_RESET = "r";
  private static final String SERVER_CONTROL_MESSAGE_HELLO = "h";
  private static final String SERVER_CONTROL_MESSAGE_DATA = "d";
  private static final String SERVER_HELLO_TIMESTAMP = "ts";
  private static final String SERVER_HELLO_HOST = "h";
  private static final String SERVER_HELLO_SESSION_ID = "s";

  private static final Logger logger = LoggerFactory.getLogger(Connection.class);

  private static long connectionIds = 0;

  private final HostInfo hostInfo;
  private final Delegate delegate;
  private final String label;

  private WebsocketConnection conn;
  private State state;

  Connection(
      ConnectionContext context,
      HostInfo hostInfo,
      String cachedHost,
      Delegate delegate,
      String optLastSessionId) {
    this(hostInfo, delegate,
        new DefaultWebsocketConnectionFactory(context, hostInfo, cachedHost, optLastSessionId));
  }

  @VisibleForTesting
  Connection(
      HostInfo hostInfo,
      Delegate delegate,
      WebsocketConnectionFactory connFactory) {
    long connId = connectionIds++;
    this.hostInfo = hostInfo;
    this.delegate = delegate;
    this.label = "[conn_" + connId + "]";
    this.state = State.REALTIME_CONNECTING;
    this.conn = connFactory.newConnection(this);
  }

  public void open() {
    logger.debug("{} Opening a connection", label);
    conn.open();
  }

  public void close(DisconnectReason reason) {
    if (state != State.REALTIME_DISCONNECTED) {
      logger.debug("{} Closing realtime connection", label);
      state = State.REALTIME_DISCONNECTED;

      if (conn != null) {
        conn.close();
        conn = null;
      }

      delegate.onDisconnect(reason);
    }
  }

  public void close() {
    close(DisconnectReason.OTHER);
  }

  public void sendRequest(Map<String, Object> message, boolean isSensitive) {
    // This came from the persistent connection. Wrap it in an envelope and send it

    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_TYPE, REQUEST_TYPE_DATA);
    request.put(REQUEST_PAYLOAD, message);

    sendData(request, isSensitive);
  }

  @Override
  public void onMessage(Map<String, Object> message) {
    try {
      String messageType = (String) message.get(SERVER_ENVELOPE_TYPE);
      if (messageType != null) {
        if (messageType.equals(SERVER_DATA_MESSAGE)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) message.get(SERVER_ENVELOPE_DATA);
          onDataMessage(data);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) message.get(SERVER_ENVELOPE_DATA);
          onControlMessage(data);
        } else {
          logger.debug("{} Ignoring unknown server message type: {}", label, messageType);
        }
      } else {
        logger.debug("{} Failed to parse server message: missing message type: {}", label, message);
        close();
      }
    } catch (ClassCastException e) {
      logger.debug("{} Failed to parse server message", label, e);
      close();
    }
  }

  @Override
  public void onDisconnect(boolean wasEverConnected) {
    conn = null;
    if (!wasEverConnected && state == State.REALTIME_CONNECTING) {
      logger.debug("{} Realtime connection failed", label);
    } else {
      logger.debug("{} Realtime connection lost", label);
    }

    close();
  }

  private void onDataMessage(Map<String, Object> data) {
    logger.debug("{} Received data message: {}", label, data);
    // We don't do anything with data messages, just kick them up a level
    delegate.onDataMessage(data);
  }

  private void onControlMessage(Map<String, Object> data) {
    logger.debug("{} Got control message: {}", label, data);
    try {
      String messageType = (String) data.get(SERVER_CONTROL_MESSAGE_TYPE);
      if (messageType != null) {
        if (messageType.equals(SERVER_CONTROL_MESSAGE_SHUTDOWN)) {
          String reason = (String) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onConnectionShutdown(reason);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE_RESET)) {
          String host = (String) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onReset(host);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE_HELLO)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> handshakeData =
              (Map<String, Object>) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onHandshake(handshakeData);
        } else {
          logger.debug("{} Ignoring unknown control message: {}", label, messageType);
        }
      } else {
        logger.debug("{} Got invalid control message: {}", label, data);
        close();
      }
    } catch (ClassCastException e) {
      logger.debug("{} Failed to parse control message", label, e);
      close();
    }
  }

  private void onConnectionShutdown(String reason) {
    logger.debug("{} Connection shutdown command received. Shutting down...", label);
    delegate.onKill(reason);
    close();
  }

  private void onHandshake(Map<String, Object> handshake) {
    long timestamp = (Long) handshake.get(SERVER_HELLO_TIMESTAMP);
    String host = (String) handshake.get(SERVER_HELLO_HOST);
    delegate.onCacheHost(host);
    String sessionId = (String) handshake.get(SERVER_HELLO_SESSION_ID);

    if (state == State.REALTIME_CONNECTING) {
      conn.start();
      onConnectionReady(timestamp, sessionId);
    }
  }

  private void onConnectionReady(long timestamp, String sessionId) {
    logger.debug("{} Realtime connection established", label);
    state = State.REALTIME_CONNECTED;
    delegate.onReady(timestamp, sessionId);
  }

  private void onReset(String host) {
    logger.debug(
        "{} Got a reset; killing connection to {}; Updating internalHost to {}",
            label, hostInfo.getHost(), host);
    delegate.onCacheHost(host);

    // Explicitly close the connection with SERVER_RESET so calling code knows to reconnect
    // immediately.
    close(DisconnectReason.SERVER_RESET);
  }

  private void sendData(Map<String, Object> data, boolean isSensitive) {
    if (state != State.REALTIME_CONNECTED) {
      logger.debug("{} Tried to send on an unconnected connection", label);
    } else {
      if (isSensitive) {
        logger.debug("{} Sending data (contents hidden)", label);
      } else {
        logger.debug("{} Sending data: {}", label, data);
      }
      conn.send(data);
    }
  }

  public enum DisconnectReason {
    SERVER_RESET,
    OTHER
  }

  private enum State {
    REALTIME_CONNECTING,
    REALTIME_CONNECTED,
    REALTIME_DISCONNECTED
  }

  interface WebsocketConnectionFactory {
    WebsocketConnection newConnection(WebsocketConnection.Delegate delegate);
  }

  private static class DefaultWebsocketConnectionFactory implements WebsocketConnectionFactory {

    final ConnectionContext context;
    final HostInfo hostInfo;
    final String cachedHost;
    final String optLastSessionId;

    DefaultWebsocketConnectionFactory(
        ConnectionContext context,
        HostInfo hostInfo,
        String cachedHost,
        String optLastSessionId) {
      this.context = context;
      this.hostInfo = hostInfo;
      this.cachedHost = cachedHost;
      this.optLastSessionId = optLastSessionId;
    }

    @Override
    public WebsocketConnection newConnection(WebsocketConnection.Delegate delegate) {
      return new WebsocketConnection(context, hostInfo, cachedHost, delegate, optLastSessionId);
    }
  }

  public interface Delegate {

    void onCacheHost(String host);

    void onReady(long timestamp, String sessionId);

    void onDataMessage(Map<String, Object> message);

    void onDisconnect(DisconnectReason reason);

    void onKill(String reason);
  }
}
