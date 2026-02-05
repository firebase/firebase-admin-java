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

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;

public class ApacheHttp2Response extends LowLevelHttpResponse {
  private final SimpleHttpResponse response;
  private final Header[] allHeaders;

  ApacheHttp2Response(SimpleHttpResponse response) {
    this.response = response;
    allHeaders = response.getHeaders();
  }

  @Override
  public int getStatusCode() {
    return response.getCode();
  }

  @Override
  public InputStream getContent() throws IOException {
    return new ByteArrayInputStream(response.getBodyBytes());
  }

  @Override
  public String getContentEncoding() {
    Header contentEncodingHeader = response.getFirstHeader("Content-Encoding");
    return contentEncodingHeader == null ? null : contentEncodingHeader.getValue();
  }

  @Override
  public long getContentLength() {
    String bodyText = response.getBodyText();
    return bodyText == null ? 0 : bodyText.length();
  }

  @Override
  public String getContentType() {
    ContentType contentType = response.getContentType();
    return contentType == null ? null : contentType.toString();
  }

  @Override
  public String getReasonPhrase() {
    return response.getReasonPhrase();
  }

  @Override
  public String getStatusLine() {
    return response.toString();
  }

  public String getHeaderValue(String name) {
    return response.getLastHeader(name).getValue();
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
  public SimpleHttpResponse getResponse() {
    return response;
  }
}
