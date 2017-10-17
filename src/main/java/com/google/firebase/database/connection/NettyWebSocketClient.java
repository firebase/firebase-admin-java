package com.google.firebase.database.connection;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
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
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLException;

class NettyWebSocketClient implements WebsocketConnection.WSClient {

  private final URI uri;
  private final SslContext sslContext;
  private final ChannelHandler channelHandler;
  private final EventLoopGroup group;

  private Channel channel;

  NettyWebSocketClient(
      URI uri, String userAgent,
      WebsocketConnection.WSClientEventHandler eventHandler) throws SSLException {
    this.uri = checkNotNull(uri);
    this.sslContext = SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

    WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
        uri, WebSocketVersion.V13, null, true,
        new DefaultHttpHeaders().add("User-Agent", userAgent));
    this.channelHandler = new WebSocketClientHandler(eventHandler, handshaker);

    ThreadFactory factory = new ThreadFactoryBuilder()
        .setNameFormat("hkj-websocket-%d")
        .setDaemon(true)
        .build();
    this.group = new NioEventLoopGroup(1, factory);
  }

  @Override
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
                new HttpObjectAggregator(8192),
                WebSocketClientCompressionHandler.INSTANCE,
                channelHandler);
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

  private static class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebsocketConnection.WSClientEventHandler delegate;
    private final WebSocketClientHandshaker handshaker;

    WebSocketClientHandler(
        WebsocketConnection.WSClientEventHandler delegate,
        WebSocketClientHandshaker handshaker) {
      this.delegate = checkNotNull(delegate);
      this.handshaker = checkNotNull(handshaker);
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
      try {
        delegate.onClose();
      } finally {
        context.close();
      }
    }

    @Override
    public void channelRead0(ChannelHandlerContext context, Object message) throws Exception {
      Channel channel = context.channel();
      if (!handshaker.isHandshakeComplete()) {
        checkState(message instanceof FullHttpResponse);
        handshaker.finishHandshake(channel, (FullHttpResponse) message);
        delegate.onOpen();
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
        try {
          delegate.onClose();
        } finally {
          channel.close();
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, final Throwable cause) {
      try {
        delegate.onError(cause);
      } finally {
        context.close();
      }
    }
  }
}
