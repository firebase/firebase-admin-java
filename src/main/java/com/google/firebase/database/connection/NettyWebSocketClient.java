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
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLException;

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
      final SslContext sslContext = SslContextBuilder.forClient()
          .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(group)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ChannelPipeline p = ch.pipeline();
              p.addLast(sslContext.newHandler(ch.alloc(), uri.getHost(), DEFAULT_WSS_PORT));
              p.addLast(
                  new HttpClientCodec(),
                  new HttpObjectAggregator(8192),
                  WebSocketClientCompressionHandler.INSTANCE,
                  channelHandler);
            }
          });

      ChannelFuture channelFuture = bootstrap.connect(uri.getHost(), DEFAULT_WSS_PORT);
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
    } catch (SSLException e) {
      eventHandler.onError(e);
    }
  }

  @Override
  public void close() {
    checkState(channel != null, "channel not initialized");
    try {
      channel.close();
    } finally {
      group.shutdownGracefully();
      executorService.shutdown();
      // TODO(hkj): https://github.com/netty/netty/issues/7310
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
      if (!handshaker.isHandshakeComplete()) {
        checkState(message instanceof FullHttpResponse);
        try {
          handshaker.finishHandshake(channel, (FullHttpResponse) message);
          delegate.onOpen();
        } catch (WebSocketHandshakeException e) {
          delegate.onError(e);
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
        delegate.onMessage(((TextWebSocketFrame) frame).text());
      } else if (frame instanceof CloseWebSocketFrame) {
        delegate.onClose();
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, final Throwable cause) {
      delegate.onError(cause);
    }
  }
}
