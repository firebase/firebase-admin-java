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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;

public class MockApacheHttp2Response implements HttpResponse {
  List<Header> headers = new ArrayList<>();
  int code = 200;

  @Override
  public void setVersion(ProtocolVersion version) {
  }

  @Override
  public ProtocolVersion getVersion() {
    return null;
  }

  @Override
  public void addHeader(Header header) {
    headers.add(header);
  }

  @Override
  public void addHeader(String name, Object value) {
    addHeader(newHeader(name, value));
  }

  private Header newHeader(String key, Object value) {
    return new Header() {
      @Override
      public boolean isSensitive() {
        return false;
      }

      @Override
      public String getName() {
        return key;
      }

      @Override
      public String getValue() {
        return value.toString();
      }
    };
  }

  @Override
  public void setHeader(Header header) {
    if (headers.contains(header)) {
      int index = headers.indexOf(header);
      headers.set(index, header);
    } else {
      addHeader(header);
    }
  }

  @Override
  public void setHeader(String name, Object value) {
    setHeader(newHeader(name, value));
  }

  @Override
  public void setHeaders(Header... headers) {
    for (Header header : headers) {
      setHeader(header);
    }
  }

  @Override
  public boolean removeHeader(Header header) {
    if (headers.contains(header)) {
      headers.remove(headers.indexOf(header));
      return true;
    }
    return false;
  }

  @Override
  public boolean removeHeaders(String name) {
    int initialSize = headers.size();
    for (Header header : headers.stream().filter(h -> h.getName() == name)
        .collect(Collectors.toList())) {
      removeHeader(header);
    }
    return headers.size() < initialSize;
  }

  @Override
  public boolean containsHeader(String name) {
    return headers.stream().anyMatch(h -> h.getName() == name);
  }

  @Override
  public int countHeaders(String name) {
    return headers.size();
  }

  @Override
  public Header getFirstHeader(String name) {
    return headers.stream().findFirst().orElse(null);
  }

  @Override
  public Header getHeader(String name) throws ProtocolException {
    return headers.stream().filter(h -> h.getName() == name).findFirst().orElse(null);
  }

  @Override
  public Header[] getHeaders() {
    return headers.toArray(new Header[0]);
  }

  @Override
  public Header[] getHeaders(String name) {
    return headers.stream()
        .filter(h -> h.getName() == name)
        .collect(Collectors.toList())
        .toArray(new Header[0]);
  }

  @Override
  public Header getLastHeader(String name) {
    return headers.isEmpty() ? null : headers.get(headers.size() - 1);
  }

  @Override
  public Iterator<Header> headerIterator() {
    return headers.iterator();
  }

  @Override
  public Iterator<Header> headerIterator(String name) {
    return headers.stream().filter(h -> h.getName() == name).iterator();
  }

  @Override
  public int getCode() {
    return this.code;
  }

  @Override
  public void setCode(int code) {
    this.code = code;
  }

  @Override
  public String getReasonPhrase() {
    return null;
  }

  @Override
  public void setReasonPhrase(String reason) {
  }

  @Override
  public Locale getLocale() {
    return null;
  }

  @Override
  public void setLocale(Locale loc) {
  }
}
