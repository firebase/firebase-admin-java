package com.google.firebase.database.connection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.firebase.internal.GaeThreadFactory;
import com.google.firebase.internal.RevivingScheduledExecutor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
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
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.URI;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * A {@link WebsocketConnection.WSClient} implementation based on the Netty framework. Uses
 * a single-threaded NIO event loop to read and write bytes from a WebSocket connection. Netty
 * handles all the low-level IO, SSL and WebSocket handshake, and other protocol-specific details.
 *
 * <p>This implementation does not initiate connection close on its own. In case of errors or loss
 * of connectivity, it notifies the higher layer ({@link WebsocketConnection}), which then decides
 * whether to initiate a connection tear down.
 */
class NettyWebSocketClient implements WebsocketConnection.WSClient {

  private static final int DEFAULT_WSS_PORT = 443;

  private final URI uri;
  private final WebsocketConnection.WSClientEventHandler eventHandler;
  private final ChannelHandler channelHandler;
  private final ExecutorService executorService;
  private final EventLoopGroup group;

  private Channel channel;

  NettyWebSocketClient(
      URI uri, String userAgent, ThreadFactory threadFactory,
      WebsocketConnection.WSClientEventHandler eventHandler) {
    this.uri = checkNotNull(uri, "uri must not be null");
    this.eventHandler = checkNotNull(eventHandler, "event handler must not be null");
    this.channelHandler = new WebSocketClientHandler(uri, userAgent, eventHandler);
    this.executorService = new RevivingScheduledExecutor(
        threadFactory, "firebase-websocket-worker", GaeThreadFactory.isAvailable());
    this.group = new NioEventLoopGroup(1, this.executorService);
  }

  @Override
  public void connect() {
    checkState(channel == null, "channel already initialized");
    try {
      TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm());
      trustFactory.init((KeyStore) null);
      final SslContext sslContext = SslContextBuilder.forClient()
          .trustManager(trustFactory).build();
      Bootstrap bootstrap = new Bootstrap();
      final int port = uri.getPort() != -1 ? uri.getPort() : DEFAULT_WSS_PORT;
      bootstrap.group(group)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ChannelPipeline p = ch.pipeline();
              p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), port));
              p.addLast(
                  new HttpClientCodec(),
                  // Set the max size for the HTTP responses. This only applies to the WebSocket
                  // handshake response from the server.
                  new HttpObjectAggregator(32 * 1024),
                  channelHandler);
            }
          });

      ChannelFuture channelFuture = bootstrap.connect(uri.getHost(), port);
      this.channel = channelFuture.channel();
      channelFuture.addListener(
          new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              if (!future.isSuccess()) {
                eventHandler.onError(future.cause());
              }
            }
          }
      );
    } catch (Exception e) {
      eventHandler.onError(e);
    }
  }

  @Override
  public void close() {
    checkState(channel != null, "channel not initialized");
    try {
      channel.close();
    } finally {
      // The following may leave an active threadDeathWatcher daemon behind. That can be cleaned
      // up at a higher level if necessary. See https://github.com/netty/netty/issues/7310.
      group.shutdownGracefully();
      executorService.shutdown();
    }
  }

  @Override
  public void send(String msg) {
    checkState(channel != null && channel.isActive(), "channel not connected for sending");
    channel.writeAndFlush(new TextWebSocketFrame(msg));
  }

  /**
   * Handles low-level IO events. These events fire on the firebase-websocket-worker thread. We
   * notify the {@link WebsocketConnection} on all events, which then hands them off to the
   * RunLoop for further processing.
   */
  private static class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebsocketConnection.WSClientEventHandler delegate;
    private final WebSocketClientHandshaker handshaker;

    WebSocketClientHandler(
        URI uri, String userAgent, WebsocketConnection.WSClientEventHandler delegate) {
      this.delegate = checkNotNull(delegate, "delegate must not be null");
      checkArgument(!Strings.isNullOrEmpty(userAgent), "user agent must not be null or empty");
      this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
          uri, WebSocketVersion.V13, null, true,
          new DefaultHttpHeaders().add("User-Agent", userAgent));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext context) {
      // Do nothing
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
      handshaker.handshake(context.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
      delegate.onClose();
    }

    @Override
    public void channelRead0(ChannelHandlerContext context, Object message) throws Exception {
      Channel channel = context.channel();
      if (message instanceof FullHttpResponse) {
        checkState(!handshaker.isHandshakeComplete());
        try {
          handshaker.finishHandshake(channel, (FullHttpResponse) message);
          delegate.onOpen();
        } catch (WebSocketHandshakeException e) {
          delegate.onError(e);
        }
      } else if (message instanceof TextWebSocketFrame) {
        delegate.onMessage(((TextWebSocketFrame) message).text());
      } else {
        checkState(message instanceof CloseWebSocketFrame);
        delegate.onClose();
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, final Throwable cause) {
      delegate.onError(cause);
    }
  }
}