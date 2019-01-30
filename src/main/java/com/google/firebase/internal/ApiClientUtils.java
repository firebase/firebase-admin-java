package com.google.firebase.internal;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.firebase.FirebaseApp;

import java.io.IOException;

public class ApiClientUtils {

  public static HttpRequestFactory newAuthorizedRequestFactory(FirebaseApp app) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return transport.createRequestFactory(new FirebaseRequestInitializer(app));
  }

  public static HttpRequestFactory newUnauthorizedRequestFactory(FirebaseApp app) {
    HttpTransport transport = app.getOptions().getHttpTransport();
    return transport.createRequestFactory();
  }

  public static void disconnectQuietly(HttpResponse response) {
    if (response != null) {
      try {
        response.disconnect();
      } catch (IOException ignored) {
        // ignored
      }
    }
  }
}
