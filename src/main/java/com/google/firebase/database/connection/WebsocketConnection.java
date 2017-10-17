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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.firebase.database.connection.util.StringListReader;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.util.JsonMapper;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLException;

class WebsocketConnection {

  private static final long KEEP_ALIVE_TIMEOUT_MS = 45 * 1000; // 45 seconds
  private static final long CONNECT_TIMEOUT_MS = 30 * 1000; // 30 seconds
  private static final int MAX_FRAME_SIZE = 16384;
  private static final AtomicLong CONN_ID = new AtomicLong(0);

  private final ConnectionContext connectionContext;
  private final ScheduledExecutorService executorService;
  private final LogWrapper logger;
  private final WSClient conn;
  private final Delegate delegate;
  private final AtomicLong totalFrames = new AtomicLong(0);
  private StringListReader frameReader;

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
    this.connectionContext = connectionContext;
    this.executorService = connectionContext.getExecutorService();
    this.delegate = delegate;
    this.logger = new LogWrapper(connectionContext.getLogger(), WebsocketConnection.class,
        "ws_" + CONN_ID.getAndIncrement());
    this.conn = createConnection(hostInfo, optCachedHost, optLastSessionId);
  }

  private WSClient createConnection(
      HostInfo hostInfo, String optCachedHost, String optLastSessionId) {
    String host = (optCachedHost != null) ? optCachedHost : hostInfo.getHost();
    URI uri = HostInfo.getConnectionUrl(
        host, hostInfo.isSecure(), hostInfo.getNamespace(), optLastSessionId);
    try {
      WebSocketClientHandler handler = new WebSocketClientHandler(
          uri, connectionContext.getUserAgent());
      return new NettyWebSocketClient(uri, handler);
    } catch (Exception e) {
      String msg = "Error while initializing websocket client";
      logger.error(msg, e);
      throw new RuntimeException(msg, e);
    }
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

  public void start() {
    // No-op in java
  }

  public void close() {
    if (logger.logsDebug()) {
      logger.debug("websocket is being closed");
    }
    isClosed = true;
    // Although true is passed for both of these, they each run on the same event loop, so
    // they will never be running.
    conn.close();
    if (connectTimeout != null) {
      connectTimeout.cancel(true);
    }
    if (keepAlive != null) {
      keepAlive.cancel(true);
    }
  }

  public void send(Map<String, Object> message) {
    resetKeepAlive();
    try {
      String toSend = JsonMapper.serializeJson(message);
      String[] segs = splitIntoFrames(toSend, MAX_FRAME_SIZE);
      if (segs.length > 1) {
        conn.send("" + segs.length);
      }

      for (String seg : segs) {
        if (logger.logsDebug()) {
          logger.debug("Sending segment: " + seg);
        }
        conn.send(seg);
      }
    } catch (IOException e) {
      logger.error("Failed to serialize message: " + message.toString(), e);
      shutdown();
    }
  }

  private static String[] splitIntoFrames(String src, int maxFrameSize) {
    if (src.length() <= maxFrameSize) {
      return new String[] {src};
    } else {
      List<String> segs = new ArrayList<>();
      for (int i = 0; i < src.length(); i += maxFrameSize) {
        int end = Math.min(i + maxFrameSize, src.length());
        String seg = src.substring(i, end);
        segs.add(seg);
      }
      return segs.toArray(new String[segs.size()]);
    }
  }

  private void handleNewFrameCount(int numFrames) {
    totalFrames.set(numFrames);
    frameReader = new StringListReader();
    if (logger.logsDebug()) {
      logger.debug("HandleNewFrameCount: " + totalFrames);
    }
  }

  private void appendFrame(String message) {
    frameReader.addString(message);
    if (totalFrames.decrementAndGet() == 0) {
      // Decode JSON
      try {
        frameReader.freeze();
        Map<String, Object> decoded = JsonMapper.parseJson(frameReader.toString());
        if (logger.logsDebug()) {
          logger.debug("handleIncomingFrame complete frame: " + decoded);
        }
        delegate.onMessage(decoded);
      } catch (IOException e) {
        logger.error("Error parsing frame: " + frameReader.toString(), e);
        close();
        shutdown();
      } catch (ClassCastException e) {
        logger.error("Error parsing frame (cast error): " + frameReader.toString(), e);
        close();
        shutdown();
      } finally {
        frameReader = null;
      }
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
    if (!isClosed) {
      resetKeepAlive();
      if (frameReader != null) {
        appendFrame(message);
      } else {
        String remaining = extractFrameCount(message);
        if (remaining != null) {
          appendFrame(remaining);
        }
      }
    }
  }

  private void resetKeepAlive() {
    if (!isClosed) {
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

  private void onClosed() {
    if (!isClosed) {
      if (logger.logsDebug()) {
        logger.debug("closing itself");
      }
      shutdown();
    }
    if (keepAlive != null) {
      keepAlive.cancel(false);
    }
  }

  private void shutdown() {
    isClosed = true;
    delegate.onDisconnect(everConnected);
  }

  // Close methods

  private void closeIfNeverConnected() {
    if (!everConnected && !isClosed) {
      if (logger.logsDebug()) {
        logger.debug("timed out on connect");
      }
      conn.close();
    }
  }

  public interface Delegate {

    void onMessage(Map<String, Object> message);

    void onDisconnect(boolean wasEverConnected);
  }

  private interface WSClient {

    void connect();

    void close();

    void send(String msg);
  }

  private static class NettyWebSocketClient implements WSClient {

    private final URI uri;
    private final WebSocketClientHandler eventHandler;
    private final SslContext sslContext;

    private final EventLoopGroup group;
    private Channel channel;

    NettyWebSocketClient(URI uri, WebSocketClientHandler eventHandler) throws SSLException {
      this.uri = uri;
      this.eventHandler = eventHandler;
      this.sslContext = SslContextBuilder.forClient()
          .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
      ThreadFactory factory = new ThreadFactoryBuilder()
          .setNameFormat("hkj-websocket-%d")
          .setDaemon(true)
          .build();
      this.group = new NioEventLoopGroup(1, factory);
    }

    public void connect() {
      checkState(channel == null, "channel already initialized");
      Bootstrap b = new Bootstrap();
      b.group(group)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ChannelPipeline p = ch.pipeline();
              p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), 443));
              p.addLast(
                  new HttpClientCodec(),
                  new HttpObjectAggregator(MAX_FRAME_SIZE),
                  WebSocketClientCompressionHandler.INSTANCE,
                  eventHandler);
            }
          });
      channel = b.connect(uri.getHost(), 443).channel();
    }

    @Override
    public void close() {
      checkState(channel != null, "channel not initialized");
      try {
        channel.close();
      } finally {
        group.shutdownGracefully();
        // TODO(hkj): https://github.com/netty/netty/issues/7310
      }
    }

    @Override
    public void send(String msg) {
      checkState(channel != null && channel.isActive(), "channel not connected for sending");
      channel.writeAndFlush(new TextWebSocketFrame(msg));
    }
  }

  private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    WebSocketClientHandler(URI uri, String userAgent) {
      this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
          uri, WebSocketVersion.V13, null, true,
          new DefaultHttpHeaders().add("User-Agent", userAgent));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext context) {
      handshakeFuture = context.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
      handshaker.handshake(context.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
      try {
        onClose();
      } finally {
        context.close();
      }
    }

    @Override
    public void channelRead0(ChannelHandlerContext context, Object message) throws Exception {
      Channel channel = context.channel();
      if (!handshaker.isHandshakeComplete()) {
        try {
          checkState(message instanceof FullHttpResponse);
          handshaker.finishHandshake(channel, (FullHttpResponse) message);
          handshakeFuture.setSuccess();
          onOpen();
        } catch (WebSocketHandshakeException e) {
          handshakeFuture.setFailure(e);
        }
        return;
      }

      if (message instanceof FullHttpResponse) {
        FullHttpResponse response = (FullHttpResponse) message;
        String error = String.format("Unexpected FullHttpResponse (status: %s; content: %s)",
            response.status().toString(), response.content().toString(CharsetUtil.UTF_8));
        throw new IllegalStateException(error);
      }

      WebSocketFrame frame = (WebSocketFrame) message;
      if (frame instanceof TextWebSocketFrame) {
        onMessage(((TextWebSocketFrame) frame).text());
      } else if (frame instanceof CloseWebSocketFrame) {
        try {
          onClose();
        } finally {
          channel.close();
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, final Throwable cause) {
      try {
        if (!handshakeFuture.isDone()) {
          handshakeFuture.setFailure(cause);
        }
        onError(cause);
      } finally {
        context.close();
      }
    }

    private void onOpen() {
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

    private void onMessage(final String message) {
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

    private void onClose() {
      if (logger.logsDebug()) {
        logger.debug("closed");
      }
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              onClosed();
            }
          });
    }

    private void onError(final Throwable e) {
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              if (e.getCause() != null && e.getCause() instanceof EOFException) {
                logger.error("WebSocket reached EOF", e);
              } else {
                logger.error("WebSocket error", e);
              }
              onClosed();
            }
          });
    }
  }
}
