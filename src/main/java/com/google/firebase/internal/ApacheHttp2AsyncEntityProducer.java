package com.google.firebase.internal;

import com.google.api.client.util.StreamingContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

@SuppressWarnings("deprecation")
public class ApacheHttp2AsyncEntityProducer implements AsyncEntityProducer {
  private final ByteBuffer bytebuf;
  private ByteArrayOutputStream baos = new ByteArrayOutputStream();
  private final ContentType contentType;
  private final long contentLength;
  private final String contentEncoding;
  private final CompletableFuture<Void> writeFuture;
  private final AtomicReference<Exception> exception;

  public ApacheHttp2AsyncEntityProducer(StreamingContent content, ContentType contentType,
      String contentEncoding, long contentLength, CompletableFuture<Void> writeFuture) {
    this.writeFuture = writeFuture;

    if (content != null) {
      try {
        content.writeTo(baos);
      } catch (IOException e) {
        writeFuture.completeExceptionally(e);
      }
    }
    this.bytebuf = ByteBuffer.wrap(baos.toByteArray());
    this.contentType = contentType;
    this.contentLength = contentLength;
    this.contentEncoding = contentEncoding;
    this.exception = new AtomicReference<>();
  }

  public ApacheHttp2AsyncEntityProducer(ApacheHttp2Request request,
      CompletableFuture<Void> writeFuture) {
    this(
        request.getStreamingContent(),
        ContentType.parse(request.getContentType()),
        request.getContentEncoding(),
        request.getContentLength(),
        writeFuture);
  }

  @Override
  public boolean isRepeatable() {
    return false;
  }

  @Override
  public String getContentType() {
    return contentType != null ? contentType.toString() : null;
  }

  @Override
  public long getContentLength() {
    return contentLength;
  }

  @Override
  public int available() {
    return Integer.MAX_VALUE;
  }

  @Override
  public String getContentEncoding() {
    return contentEncoding;
  }

  @Override
  public boolean isChunked() {
    return false;
  }

  @Override
  public Set<String> getTrailerNames() {
    return null;
  }

  @Override
  public void produce(DataStreamChannel channel) throws IOException {
    if (bytebuf.hasRemaining()) {
      channel.write(bytebuf);
    }
    if (!bytebuf.hasRemaining()) {
      channel.endStream();
      writeFuture.complete(null);
    }
  }

  @Override
  public void failed(Exception cause) {
    if (exception.compareAndSet(null, cause)) {
      releaseResources();
      writeFuture.completeExceptionally(cause);
    }
  }

  public final Exception getException() {
    return exception.get();
  }

  @Override
  public void releaseResources() {
    bytebuf.clear();
  }
}