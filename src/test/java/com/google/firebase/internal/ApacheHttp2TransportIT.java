package com.google.firebase.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;

public class ApacheHttp2TransportIT {
  private static final GoogleCredentials MOCK_CREDENTIALS = new MockGoogleCredentials("test_token");
  private static final ImmutableMap<String, Object> payload = ImmutableMap.<String, Object>of("foo", "bar");
  // Sets a 1 second delay before response
  private static final String DELAY_URL = "https://nghttp2.org/httpbin/delay/1";
  private static final String POST_URL = "https://nghttp2.org/httpbin/post";

  @BeforeClass
  public static void setUpClass() {
  }

  @After
  public void cleanup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testUnauthorizedPostRequest() throws FirebaseException {
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(false);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(POST_URL, payload);
    GenericData body = httpClient.sendAndParse(request, GenericData.class);
    assertEquals("{\"foo\":\"bar\"}", body.get("data"));
  }

  @Test
  public void testConnectTimeoutAuthorizedGet() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setConnectTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(DELAY_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void testConnectTimeoutAuthorizedPost() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setConnectTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(DELAY_URL, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void testReadTimeoutAuthorizedGet() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setReadTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(DELAY_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void testReadTimeoutAuthorizedPost() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setReadTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(DELAY_URL, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Exception in request", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void testWriteTimeoutAuthorizedGet() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setWriteTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildGetRequest(DELAY_URL);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Timed out", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void testWriteTimeoutAuthorizedPost() throws FirebaseException {
    FirebaseApp timeoutApp = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(MOCK_CREDENTIALS)
        .setWriteTimeout(1)
        .build());
    ErrorHandlingHttpClient<FirebaseException> httpClient = getHttpClient(true, timeoutApp);
    HttpRequestInfo request = HttpRequestInfo.buildJsonPostRequest(DELAY_URL, payload);

    try {
      httpClient.send(request);
      fail("No exception thrown for HTTP error response");
    } catch (FirebaseException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals("IO error: Timed out", e.getMessage());
      assertNull(e.getHttpResponse());
    }
  }

  private static ErrorHandlingHttpClient<FirebaseException> getHttpClient(boolean authorized, FirebaseApp app) {
    HttpRequestFactory requestFactory;
    if (authorized) {
      requestFactory = ApiClientUtils.newAuthorizedRequestFactory(app);
    } else {
      requestFactory = ApiClientUtils.newUnauthorizedRequestFactory(app);
    }
    JsonFactory jsonFactory = ApiClientUtils.getDefaultJsonFactory();
    TestHttpErrorHandler errorHandler = new TestHttpErrorHandler();
    return new ErrorHandlingHttpClient<>(requestFactory, jsonFactory, errorHandler);
  }

  private static ErrorHandlingHttpClient<FirebaseException> getHttpClient(boolean authorized) {
    return getHttpClient(authorized, FirebaseApp.initializeApp(FirebaseOptions.builder()
    .setCredentials(MOCK_CREDENTIALS)
    .build(), "test-app"));
  }


  private static class TestHttpErrorHandler implements HttpErrorHandler<FirebaseException> {
    @Override
    public FirebaseException handleIOException(IOException e) {
      return new FirebaseException(
          ErrorCode.UNKNOWN, "IO error: " + e.getMessage(), e);
    }

    @Override
    public FirebaseException handleHttpResponseException(
        HttpResponseException e, IncomingHttpResponse response) {
      return new FirebaseException(
          ErrorCode.INTERNAL, "Example error message: " + e.getContent(), e, response);
    }

    @Override
    public FirebaseException handleParseException(IOException e, IncomingHttpResponse response) {
      return new FirebaseException(ErrorCode.UNKNOWN, "Parse error", e, response);
    }
  }
}
