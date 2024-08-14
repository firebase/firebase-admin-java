package com.google.firebase.internal;

import com.google.api.client.http.LowLevelHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.Header;

public class ApacheHttp2Response extends LowLevelHttpResponse {

  private final SimpleHttpResponse response;
  private final Header[] allHeaders;

  ApacheHttp2Response(SimpleHttpRequest request, SimpleHttpResponse response) {
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
    if (contentEncodingHeader == null) {
      return null;
    }
    return contentEncodingHeader.getValue();
  }

  @Override
  public long getContentLength() {
    return response.getBodyText().length();
  }

  @Override
  public String getContentType() {
    return response.getContentType().toString();
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
    Header header = response.getLastHeader(name);
    if (header == null) {
      return null;
    }
    return header.getValue();
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
}
