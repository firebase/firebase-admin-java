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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.util.JsonMapper;

import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a WebSocket connection to the Firebase Realtime Database. This abstraction acts as
 * the mediator between low-level IO ({@link WSClient}), and high-level connection management
 * ({@link Connection}). It handles frame buffering, most of the low-level errors, and notifies the
 * higher layer when necessary. Higher layer signals this implementation when it needs to send a
 * message out, or when a graceful connection tear down should be initiated.
 */
class WebsocketConnection {

  private static final long KEEP_ALIVE_TIMEOUT_MS = 45 * 1000; // 45 seconds
  private static final long CONNECT_TIMEOUT_MS = 30 * 1000; // 30 seconds
  private static final int MAX_FRAME_SIZE = 16384;
  private static final AtomicLong CONN_ID = new AtomicLong(0);

  private final ScheduledExecutorService executorService;
  private final LogWrapper logger;
  private final WSClient conn;
  private final Delegate delegate;

  private StringList buffer;
  private boolean everConnected = false;
  private boolean isClosed = false;
  private ScheduledFuture<?> keepAlive;
  private ScheduledFuture<?> connectTimeout;

  WebsocketConnection(
      ConnectionContext connectionContext,
      HostInfo hostInfo,
      String optCachedHost,
      Delegate delegate,
      String optLastSessionId) {
    this(connectionContext, delegate,
        new DefaultWSClientFactory(connectionContext, hostInfo, optCachedHost, optLastSessionId));
  }

  WebsocketConnection(
      ConnectionContext connectionContext,
      Delegate delegate,
      WSClientFactory clientFactory) {
    this.executorService = connectionContext.getExecutorService();
    this.delegate = delegate;
    this.logger = new LogWrapper(connectionContext.getLogger(), WebsocketConnection.class,
        "ws_" + CONN_ID.getAndIncrement());
    this.conn = clientFactory.newClient(new WSClientHandlerImpl());
  }

  void open() {
    conn.connect();
    connectTimeout =
        executorService.schedule(
            new Runnable() {
              @Override
              public void run() {
                closeIfNeverConnected();
              }
            },
            CONNECT_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);
  }

  void start() {
    // No-op in java
  }

  void close() {
    if (logger.logsDebug()) {
      logger.debug("websocket is being closed");
    }
    isClosed = true;
    conn.close();

    // Although true is passed for both of these, they each run on the same event loop, so
    // they will never be running.
    if (connectTimeout != null) {
      connectTimeout.cancel(true);
      connectTimeout = null;
    }
    if (keepAlive != null) {
      keepAlive.cancel(true);
      keepAlive = null;
    }
  }

  void send(Map<String, Object> message) {
    resetKeepAlive();
    try {
      String toSend = JsonMapper.serializeJson(message);
      List<String> frames = splitIntoFrames(toSend, MAX_FRAME_SIZE);
      if (frames.size() > 1) {
        conn.send("" + frames.size());
      }

      for (String seg : frames) {
        conn.send(seg);
      }
    } catch (IOException e) {
      logger.error("Failed to serialize message: " + message.toString(), e);
      closeAndNotify();
    }
  }

  private List<String> splitIntoFrames(String src, int maxFrameSize) {
    if (src.length() <= maxFrameSize) {
      return ImmutableList.of(src);
    } else {
      ImmutableList.Builder<String> frames = ImmutableList.builder();
      for (int i = 0; i < src.length(); i += maxFrameSize) {
        int end = Math.min(i + maxFrameSize, src.length());
        String seg = src.substring(i, end);
        frames.add(seg);
      }
      return frames.build();
    }
  }

  private void handleNewFrameCount(int numFrames) {
    if (logger.logsDebug()) {
      logger.debug("HandleNewFrameCount: " + numFrames);
    }
    buffer = new StringList(numFrames);
  }

  private void appendFrame(String message) {
    int framesRemaining = buffer.append(message);
    if (framesRemaining > 0) {
      return;
    }
    // Decode JSON
    String combined = buffer.combine();
    try {
      Map<String, Object> decoded = JsonMapper.parseJson(combined);
      if (logger.logsDebug()) {
        logger.debug("handleIncomingFrame complete frame: " + decoded);
      }
      delegate.onMessage(decoded);
    } catch (IOException e) {
      logger.error("Error parsing frame: " + combined, e);
      closeAndNotify();
    } catch (ClassCastException e) {
      logger.error("Error parsing frame (cast error): " + combined, e);
      closeAndNotify();
    }
  }

  private String extractFrameCount(String message) {
    // TODO: The server is only supposed to send up to 9999 frames (i.e. length <= 4), but that
    // isn't being enforced currently.  So allowing larger frame counts (length <= 6).
    // See https://app.asana.com/0/search/8688598998380/8237608042508
    if (message.length() <= 6) {
      try {
        int frameCount = Integer.parseInt(message);
        if (frameCount > 0) {
          handleNewFrameCount(frameCount);
        }
        return null;
      } catch (NumberFormatException e) {
        // not a number, default to frame count 1
      }
    }
    handleNewFrameCount(1);
    return message;
  }

  private void handleIncomingFrame(String message) {
    if (isClosed) {
      return;
    }
    resetKeepAlive();
    if (buffer != null && buffer.hasRemaining()) {
      appendFrame(message);
    } else {
      String remaining = extractFrameCount(message);
      if (remaining != null) {
        appendFrame(remaining);
      }
    }
  }

  private void resetKeepAlive() {
    if (isClosed) {
      return;
    }
    if (keepAlive != null) {
      keepAlive.cancel(false);
      if (logger.logsDebug()) {
        logger.debug("Reset keepAlive. Remaining: " + keepAlive.getDelay(TimeUnit.MILLISECONDS));
      }
    } else {
      if (logger.logsDebug()) {
        logger.debug("Reset keepAlive");
      }
    }
    keepAlive = executorService.schedule(nop(), KEEP_ALIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
  }

  private Runnable nop() {
    return new Runnable() {
      @Override
      public void run() {
        if (conn != null) {
          conn.send("0");
          resetKeepAlive();
        }
      }
    };
  }

  /**
   * Closes the low-level connection, and notifies the higher layer ({@link Connection}.
   */
  private void closeAndNotify() {
    if (!isClosed) {
      close();
      delegate.onDisconnect(everConnected);
    }
  }

  private void onClosed() {
    if (!isClosed) {
      if (logger.logsDebug()) {
        logger.debug("closing itself");
      }
      closeAndNotify();
    }
  }

  private void closeIfNeverConnected() {
    if (!everConnected && !isClosed) {
      if (logger.logsDebug()) {
        logger.debug("timed out on connect");
      }
      closeAndNotify();
    }
  }

  /**
   * A client handler implementation that gets notified by the low-level WebSocket client. These
   * events fire on the same thread as the WebSocket client. We log the events on the same thread,
   * and hand them off to the RunLoop for further processing.
   */
  private class WSClientHandlerImpl implements WSClientEventHandler {

    @Override
    public void onOpen() {
      if (logger.logsDebug()) {
        logger.debug("websocket opened");
      }
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          connectTimeout.cancel(false);
          everConnected = true;
          resetKeepAlive();
        }
      });
    }

    @Override
    public void onMessage(final String message) {
      if (logger.logsDebug()) {
        logger.debug("ws message: " + message);
      }
      executorService.execute(new Runnable() {
        @Override
        public void run() {
          handleIncomingFrame(message);
        }
      });
    }

    @Override
    public void onClose() {
      if (logger.logsDebug()) {
        logger.debug("closed");
      }
      if (!isClosed) {
        // If the connection tear down was initiated by the higher-layer, isClosed will already
        // be true. Nothing more to do in that case.
        executorService.execute(
            new Runnable() {
              @Override
              public void run() {
                onClosed();
              }
            });
      }
    }

    @Override
    public void onError(final Throwable e) {
      if (e.getCause() != null && e.getCause() instanceof EOFException) {
        logger.error("WebSocket reached EOF", e);
      } else {
        logger.error("WebSocket error", e);
      }
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              onClosed();
            }
          });
    }
  }

  /**
   * Handles buffering of WebSocket frames. The database server breaks large messages into smaller
   * frames. This class accumulates them in memory, and reconstructs the original message
   * before passing it to the higher layers of the client for processing.
   */
  private static class StringList {

    private final List<String> buffer;
    private int remaining;

    StringList(int capacity) {
      checkArgument(capacity > 0);
      this.buffer = new ArrayList<>(capacity);
      this.remaining = capacity;
    }

    int append(String frame) {
      checkState(hasRemaining());
      buffer.add(frame);
      return --remaining;
    }

    boolean hasRemaining() {
      return remaining > 0;
    }

    /**
     * Combines frames (message fragments) received so far into a single text message. It is an
     * error to call this before receiving all the frames needed to reconstruct the message.
     */
    String combine() {
      checkState(!hasRemaining());
      try {
        StringBuilder sb = new StringBuilder();
        for (String frame : buffer) {
          sb.append(frame);
        }
        return sb.toString();
      } finally {
        buffer.clear();
      }
    }
  }

  /**
   * Higher-level event handler ({@link Connection})
   */
  interface Delegate {

    void onMessage(Map<String, Object> message);

    void onDisconnect(boolean wasEverConnected);
  }

  /**
   * Low-level WebSocket client. Implementations handle low-level network IO.
   */
  interface WSClient {

    void connect();

    void close();

    void send(String msg);
  }

  interface WSClientFactory {

    WSClient newClient(
        WSClientEventHandler delegate);

  }

  private static class DefaultWSClientFactory implements WSClientFactory {

    final ConnectionContext context;
    final HostInfo hostInfo;
    final String optCachedHost;
    final String optLastSessionId;

    DefaultWSClientFactory(ConnectionContext context, HostInfo hostInfo, String
        optCachedHost, String optLastSessionId) {
      this.context = context;
      this.hostInfo = hostInfo;
      this.optCachedHost = optCachedHost;
      this.optLastSessionId = optLastSessionId;
    }

    public WSClient newClient(WSClientEventHandler delegate) {
      String host = (optCachedHost != null) ? optCachedHost : hostInfo.getHost();
      URI uri = HostInfo.getConnectionUrl(
          host, hostInfo.isSecure(), hostInfo.getNamespace(), optLastSessionId);
      return new NettyWebSocketClient(
          uri, context.getUserAgent(), context.getThreadFactory(), delegate);
    }
  }

  /**
   * Event handler that handles the events generated by a low-level {@link WSClient}.
   */
  interface WSClientEventHandler {

    void onOpen();

    void onMessage(String message);

    void onClose();

    void onError(Throwable t);
  }
}