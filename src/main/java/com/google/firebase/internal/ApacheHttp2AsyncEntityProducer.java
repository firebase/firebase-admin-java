/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.internal;

import com.google.api.client.util.StreamingContent;
import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

public class ApacheHttp2AsyncEntityProducer implements AsyncEntityProducer {
  private ByteBuffer bytebuf;
  private ByteArrayOutputStream baos;
  private final StreamingContent content;
  private final ContentType contentType;
  private final long contentLength;
  private final String contentEncoding;
  private final CompletableFuture<Void> writeFuture;
  private final AtomicReference<Exception> exception;

  public ApacheHttp2AsyncEntityProducer(StreamingContent content, ContentType contentType,
      String contentEncoding, long contentLength, CompletableFuture<Void> writeFuture) {
    this.content = content;
    this.contentType = contentType;
    this.contentEncoding = contentEncoding;
    this.contentLength = contentLength;
    this.writeFuture = writeFuture;
    this.bytebuf = null;

    this.baos = new ByteArrayOutputStream((int) (contentLength < 0 ? 0 : contentLength));
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
    return true;
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
    return contentLength == -1;
  }

  @Override
  public Set<String> getTrailerNames() {
    return null;
  }

  @Override
  public void produce(DataStreamChannel channel) throws IOException {
    if (bytebuf == null) {
      if (content != null) {
        try {
          content.writeTo(baos);
        } catch (IOException e) {
          failed(e);
          throw e;
        }
      }

      this.bytebuf = ByteBuffer.wrap(baos.toByteArray());
    }

    if (bytebuf.hasRemaining()) {
      channel.write(bytebuf);
    }

    if (!bytebuf.hasRemaining()) {
      channel.endStream();
      writeFuture.complete(null);
      releaseResources();
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
    if (bytebuf != null) {
      bytebuf.clear();
    }
  }

  @VisibleForTesting
  ByteBuffer getBytebuf() {
    return bytebuf;
  }
}
