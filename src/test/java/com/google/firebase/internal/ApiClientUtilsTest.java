/*
 * Copyright 2019 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.RetryInitializer.RetryHandlerDecorator;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.remoteconfig.Template;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;
import org.junit.After;
import org.junit.Test;

public class ApiClientUtilsTest {

  private static final FirebaseOptions TEST_OPTIONS = FirebaseOptions.builder()
      .setCredentials(new MockGoogleCredentials("test-token"))
      .build();
  private static final GenericUrl TEST_URL = new GenericUrl("https://firebase.google.com");

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testAuthorizedHttpClient() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);

    assertTrue(requestFactory.getInitializer() instanceof FirebaseRequestInitializer);
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertTrue(retryHandler instanceof RetryHandlerDecorator);
    RetryConfig retryConfig = ((RetryHandlerDecorator) retryHandler).getRetryHandler()
        .getRetryConfig();
    assertEquals(4, retryConfig.getMaxRetries());
    assertEquals(60 * 1000, retryConfig.getMaxIntervalMillis());
    assertFalse(retryConfig.isRetryOnIOExceptions());
    assertEquals(retryConfig.getRetryStatusCodes(), ImmutableList.of(503));
  }

  @Test
  public void testAuthorizedHttpClientWithoutRetry() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app, null);

    assertTrue(requestFactory.getInitializer() instanceof FirebaseRequestInitializer);
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertFalse(retryHandler instanceof RetryHandlerDecorator);
  }

  @Test
  public void testUnauthorizedHttpClient() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);

    HttpRequestFactory requestFactory = ApiClientUtils.newUnauthorizedRequestFactory(app);

    assertNull(requestFactory.getInitializer());
    HttpRequest request = requestFactory.buildGetRequest(TEST_URL);
    assertNull(request.getHeaders().getAuthorization());
    HttpUnsuccessfulResponseHandler retryHandler = request.getUnsuccessfulResponseHandler();
    assertNull(retryHandler);
  }

  @Test
  public void testDisconnect() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse();
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(lowLevelResponse)
        .build();
    HttpResponse response = transport.createRequestFactory().buildGetRequest(TEST_URL).execute();
    assertFalse(lowLevelResponse.isDisconnected());

    ApiClientUtils.disconnectQuietly(response);

    assertTrue(lowLevelResponse.isDisconnected());
  }

  @Test
  public void testDisconnectWithErrorSuppression() throws IOException {
    MockLowLevelHttpResponse lowLevelResponse = new MockLowLevelHttpResponse() {
      @Override
      public void disconnect() throws IOException {
        super.disconnect();
        throw new IOException("test error");
      }
    };
    MockHttpTransport transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(lowLevelResponse)
        .build();
    HttpResponse response = transport.createRequestFactory().buildGetRequest(TEST_URL).execute();
    assertFalse(lowLevelResponse.isDisconnected());

    ApiClientUtils.disconnectQuietly(response);

    assertTrue(lowLevelResponse.isDisconnected());
  }

  @Test
  public void testVerifyDefaultTransportReused() {
    HttpTransport t1 = ApiClientUtils.getDefaultTransport();
    HttpTransport t2 = ApiClientUtils.getDefaultTransport();
    assertEquals(t1, t2);
  }

  @Test
  public void testDefaultJsonFactoryUsesGsonAndReused() throws IOException {
    JsonFactory f1 = ApiClientUtils.getDefaultJsonFactory();
    JsonFactory f2 = ApiClientUtils.getDefaultJsonFactory();
    assertTrue(f1 instanceof GsonFactory);
    assertSame(f1, f2);
    assertEquals("0.2", f1.toString(0.2f));
    assertThrows(
        IOException.class,
        () -> f1.createJsonParser("not json").parseAndClose(new HashMap<String, Object>()));
    assertThrows(
        IOException.class,
        () -> f1.createJsonParser("not json").parseAndClose(GenericJson.class));
  }

  @Test
  public void testMessagingWorksWithoutJackson2OnClasspath() throws Exception {
    ClassLoader isolatedLoader = new PackageBlockingClassLoader(
        ApiClientUtilsTest.class.getClassLoader(),
        ImmutableSet.of(
            "com.google.api.client.json.jackson2.",
            "com.fasterxml.jackson."));
    assertThrows(
        ClassNotFoundException.class,
        () -> isolatedLoader.loadClass("com.google.api.client.json.jackson2.JacksonFactory"));

    Class<?> probe = isolatedLoader.loadClass(JacksonExcludedProbe.class.getName());
    Method method = probe.getMethod("sendWithDefaultJsonFactory");
    String messageId = (String) method.invoke(null);
    assertEquals("projects/test-project/messages/msg-123", messageId);
  }

  @Test
  public void testCustomJsonFactoryDoesNotLoadDefaultJsonFactoryImpl() throws Exception {
    ClassLoader isolatedLoader = new PackageBlockingClassLoader(
        ApiClientUtilsTest.class.getClassLoader(),
        ImmutableSet.of(
            "com.google.api.client.json.jackson2.",
            "com.fasterxml.jackson.",
            "com.google.api.client.json.gson."));
    assertThrows(
        ClassNotFoundException.class,
        () -> isolatedLoader.loadClass("com.google.api.client.json.gson.GsonFactory"));

    Class<?> probe = isolatedLoader.loadClass(JacksonExcludedProbe.class.getName());
    Method method = probe.getMethod("sendWithCustomJsonFactory", JsonFactory.class);
    String messageId = (String) method.invoke(null, GsonFactory.getDefaultInstance());
    assertEquals("projects/test-project/messages/msg-456", messageId);
  }

  /**
   * Loaded inside {@link PackageBlockingClassLoader} to verify SDK initialization and FCM requests
   * when optional JSON parser artifacts are absent from the classpath.
   */
  public static final class JacksonExcludedProbe {

    private JacksonExcludedProbe() { }

    /**
     * Exercises default JsonFactory paths (FCM send and Remote Config Template) without Jackson2.
     */
    public static String sendWithDefaultJsonFactory() throws Exception {
      Template template = Template.fromJSON("{\"etag\": \"etag-123\"}");
      assertEquals("etag-123", template.getETag());
      assertNotNull(template.toJSON());

      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setContent("{\"name\": \"projects/test-project/messages/msg-123\"}");
      HttpTransport transport = new MockHttpTransport.Builder()
          .setLowLevelHttpResponse(response)
          .build();
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .setHttpTransport(transport)
          .build();
      FirebaseApp app = FirebaseApp.initializeApp(options);
      try {
        return FirebaseMessaging.getInstance(app)
            .send(Message.builder().setTopic("test-topic").build());
      } finally {
        TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
      }
    }

    /** Sends an FCM message using an explicit JsonFactory when default impls are absent. */
    public static String sendWithCustomJsonFactory(JsonFactory jsonFactory) throws Exception {
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
          .setContent("{\"name\": \"projects/test-project/messages/msg-456\"}");
      HttpTransport transport = new MockHttpTransport.Builder()
          .setLowLevelHttpResponse(response)
          .build();
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .setHttpTransport(transport)
          .setJsonFactory(jsonFactory)
          .build();
      FirebaseApp app = FirebaseApp.initializeApp(options);
      try {
        return FirebaseMessaging.getInstance(app)
            .send(Message.builder().setTopic("test-topic").build());
      } finally {
        TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
      }
    }
  }

  private static final class PackageBlockingClassLoader extends ClassLoader {

    private final Set<String> blockedPrefixes;

    PackageBlockingClassLoader(ClassLoader parent, Set<String> blockedPrefixes) {
      super(parent);
      this.blockedPrefixes = blockedPrefixes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        for (String prefix : blockedPrefixes) {
          if (name.startsWith(prefix)) {
            throw new ClassNotFoundException(name);
          }
        }
        if (name.startsWith("com.google.firebase.")) {
          String resourcePath = name.replace('.', '/') + ".class";
          try (InputStream in = getParent().getResourceAsStream(resourcePath)) {
            if (in == null) {
              throw new ClassNotFoundException(name);
            }
            byte[] bytes = ByteStreams.toByteArray(in);
            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            if (resolve) {
              resolveClass(defined);
            }
            return defined;
          } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
          }
        }
        return super.loadClass(name, resolve);
      }
    }
  }

}
