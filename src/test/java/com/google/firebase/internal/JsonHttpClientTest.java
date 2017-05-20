package com.google.firebase.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.GenericJson;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class JsonHttpClientTest {

  @Test
  public void testClientBuilder() {
    try {
      new JsonHttpClient.Builder().build();
      fail("No error thrown for invalid build (no host and port)");
    } catch (IllegalArgumentException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setHost("hostname")
          .build();
      fail("No error thrown for invalid build (no port)");
    } catch (IllegalArgumentException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setPort(80)
          .build();
      fail("No error thrown for invalid build (no host)");
    } catch (IllegalArgumentException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setHost("")
          .setPort(80)
          .build();
      fail("No error thrown for invalid build (empty host)");
    } catch (IllegalArgumentException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setHost("hostname")
          .setPort(-80)
          .build();
      fail("No error thrown for invalid build (invalid port)");
    } catch (IllegalArgumentException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setHost("hostname")
          .setPort(80)
          .setTransport(null)
          .build();
      fail("No error thrown for invalid build (null transport)");
    } catch (NullPointerException ignore) {
      // expected
    }

    try {
      new JsonHttpClient.Builder()
          .setHost("hostname")
          .setPort(80)
          .setJsonFactory(null)
          .build();
      fail("No error thrown for invalid build (null json factory)");
    } catch (NullPointerException ignore) {
      // expected
    }

    // should not throw
    new JsonHttpClient.Builder()
        .setHost("hostname")
        .setPort(80)
        .build();
  }

  @Test
  public void testHttpPost() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"foo\":\"bar\"}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    final AtomicReference<HttpRequest> ref = new AtomicReference<>();
    HttpExecuteInterceptor interceptor = new HttpExecuteInterceptor() {
      @Override
      public void intercept(HttpRequest request) throws IOException {
        ref.set(request);
      }
    };
    JsonHttpClient client = getTestClient(transport, interceptor);
    GenericJson json = client.post("path", null, "payload", GenericJson.class);
    assertEquals("bar", json.get("foo"));
    assertEquals("POST", ref.get().getRequestMethod());
    assertEquals("https://test.com:443/path", ref.get().getUrl().toString());
    assertNull(ref.get().getHeaders().getAuthorization());
    assertTrue(ref.get().getContent() instanceof JsonHttpContent);
  }

  @Test
  public void testHttpPostWithToken() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"foo\":\"bar\"}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    final AtomicReference<HttpRequest> ref = new AtomicReference<>();
    HttpExecuteInterceptor interceptor = new HttpExecuteInterceptor() {
      @Override
      public void intercept(HttpRequest request) throws IOException {
        ref.set(request);
      }
    };
    JsonHttpClient client = getTestClient(transport, interceptor);
    GenericJson json = client.post("path", "token", "payload", GenericJson.class);
    assertEquals("bar", json.get("foo"));
    assertEquals("POST", ref.get().getRequestMethod());
    assertEquals("https://test.com:443/path", ref.get().getUrl().toString());
    assertEquals("Bearer token", ref.get().getHeaders().getAuthorization());
    assertTrue(ref.get().getContent() instanceof JsonHttpContent);
  }

  @Test
  public void testJsonSerialization() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"name\":\"foo\", \"age\":\"25\"}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    final AtomicReference<HttpRequest> ref = new AtomicReference<>();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    HttpExecuteInterceptor interceptor = new HttpExecuteInterceptor() {
      @Override
      public void intercept(HttpRequest request) throws IOException {
        request.getContent().writeTo(out);
        ref.set(request);
      }
    };
    JsonHttpClient client = getTestClient(transport, interceptor);
    TestBean bean = new TestBean();
    bean.name = "bar";
    bean.age = 30;
    client.post("path", null, bean, GenericJson.class);

    TestBean serialized = Utils.getDefaultJsonFactory().fromString(
        new String(out.toByteArray()), TestBean.class);
    assertEquals("bar", serialized.name);
    assertEquals(30, serialized.age);
  }

  @Test
  public void testJsonDeserialization() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"name\":\"foo\", \"age\":\"25\"}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    JsonHttpClient client = getTestClient(transport, null);
    TestBean bean = client.post("path", null, "payload", TestBean.class);
    assertEquals("foo", bean.name);
    assertEquals(25, bean.age);
  }

  @Test
  public void testHttpError() throws Exception {
    for (int code : ImmutableList.of(500, 404, 400, 401, 302)) {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
      response.setStatusCode(code);
      response.setContent("{\"foo\":\"bar\"}");
      MockHttpTransport transport = new MockHttpTransport.Builder()
          .setLowLevelHttpResponse(response)
          .build();
      JsonHttpClient client = getTestClient(transport, null);
      try {
        client.post("path", null, "payload", GenericJson.class);
        fail("No error thrown for http error: " + code);
      } catch (IOException ignore) {
        // expected
      }
    }
  }

  @Test
  public void testInvalidJsonResponse() throws Exception {
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContent("{\"not\"json}");
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(response)
        .build();
    JsonHttpClient client = getTestClient(transport, null);
    try {
      client.post("path", null, "payload", GenericJson.class);
      fail("No error thrown for invalid json");
    } catch (Exception ignore) {
      // expected
    }
  }

  private JsonHttpClient getTestClient(
      HttpTransport transport, HttpExecuteInterceptor interceptor) {
    return new JsonHttpClient.Builder()
        .setHost("test.com")
        .setPort(443)
        .setSecure(true)
        .setInterceptor(interceptor)
        .setTransport(transport)
        .build();
  }

  public static class TestBean {
    @Key
    private String name;

    @Key
    private int age;
  }

}
