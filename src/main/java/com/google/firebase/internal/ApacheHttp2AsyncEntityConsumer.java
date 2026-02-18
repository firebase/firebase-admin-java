/*
 * Copyright 2026 Google LLC
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

public class ApacheHttp2AsyncEntityConsumer implements AsyncEntityConsumer<ApacheHttp2Entity> {

  private EntityDetails entityDetails;
  private FutureCallback<ApacheHttp2Entity> resultCallback;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  @Override
  public void streamStart(
      final EntityDetails entityDetails,
      final FutureCallback<ApacheHttp2Entity> resultCallback)
      throws HttpException, IOException {
    this.entityDetails = entityDetails;
    this.resultCallback = resultCallback;
  }

  @Override
  public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
    capacityChannel.update(Integer.MAX_VALUE);
  }

  @Override
  public void consume(ByteBuffer src) throws IOException {
    if (src.hasArray()) {
      buffer.write(src.array(), src.arrayOffset() + src.position(), src.remaining());
      src.position(src.limit());
    } else {
      byte[] bytes = new byte[src.remaining()];
      src.get(bytes);
      buffer.write(bytes);
    }
  }

  @Override
  public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
    if (resultCallback != null) {
      resultCallback.completed(getContent());
    }
  }

  @Override
  public void failed(Exception cause) {
    if (resultCallback != null) {
      resultCallback.failed(cause);
    }
  }

  @Override
  public void releaseResources() {
    buffer.reset();
  }

  @Override
  public ApacheHttp2Entity getContent() {
    return new ApacheHttp2Entity(buffer.toByteArray(), entityDetails);
  }
}
