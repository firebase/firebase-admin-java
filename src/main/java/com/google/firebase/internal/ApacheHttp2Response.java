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

import com.google.api.client.http.LowLevelHttpResponse;
import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.message.StatusLine;

public class ApacheHttp2Response extends LowLevelHttpResponse {
  private final Message<HttpResponse, ApacheHttp2Entity> message;
  private final HttpResponse response;
  private final Header[] allHeaders;
  private final EntityDetails entity;
  private final byte[] content;

  ApacheHttp2Response(Message<HttpResponse, ApacheHttp2Entity> message) {
    this.message = message;
    this.response = message.getHead();
    this.allHeaders = response.getHeaders();

    ApacheHttp2Entity body = message.getBody();
    this.entity = body != null ? body.getEntityDetails() : null;
    this.content = body != null ? body.getContent() : null;
  }

  @Override
  public int getStatusCode() {
    return response.getCode();
  }

  @Override
  public InputStream getContent() throws IOException {
    return content == null ? null : new ByteArrayInputStream(content);
  }

  @Override
  public String getContentEncoding() {
    return entity == null ? null : entity.getContentEncoding();
  }

  @Override
  public long getContentLength() {
    if (content != null) {
      return content.length;
    }
    return entity == null ? -1 : entity.getContentLength();
  }

  @Override
  public String getContentType() {
    return entity == null ? null : entity.getContentType();
  }

  @Override
  public String getReasonPhrase() {
    return response.getReasonPhrase();
  }

  @Override
  public String getStatusLine() {
    return new StatusLine(response).toString();
  }

  public String getHeaderValue(String name) {
    Header header = response.getLastHeader(name);
    return header == null ? null : header.getValue();
  }

  @Override
  public String getHeaderValue(int index) {
    return allHeaders[index].getValue();
  }

  @Override
  public int getHeaderCount() {
    return allHeaders.length;
  }

  @Override
  public String getHeaderName(int index) {
    return allHeaders[index].getName();
  }

  @VisibleForTesting
  public Message<HttpResponse, ApacheHttp2Entity> getMessage() {
    return message;
  }
}
