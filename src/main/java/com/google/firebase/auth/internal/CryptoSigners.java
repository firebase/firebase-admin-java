package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.Key;
import com.google.api.client.util.StringUtils;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseRequestInitializer;
import com.google.firebase.internal.NonNull;
import java.io.IOException;
import java.util.Map;

/**
 * A set of {@link CryptoSigner} implementations and utilities for interacting with them.
 */
class CryptoSigners {

  private static final String METADATA_SERVICE_URL =
      "http://metadata/computeMetadata/v1/instance/service-accounts/default/email";

  /**
   * A {@link CryptoSigner} implementation that uses service account credentials or equivalent
   * crypto-capable credentials for signing data.
   */
  static class ServiceAccountCryptoSigner implements CryptoSigner {

    private final ServiceAccountSigner signer;

    ServiceAccountCryptoSigner(@NonNull ServiceAccountSigner signer) {
      this.signer = checkNotNull(signer);
    }

    @Override
    public byte[] sign(byte[] payload) {
      return signer.sign(payload);
    }

    @Override
    public String getAccount() {
      return signer.getAccount();
    }
  }

  /**
   * @ {@link CryptoSigner} implementation that uses the
   * <a href="https://cloud.google.com/iam/reference/rest/v1/projects.serviceAccounts/signBlob">
   * Google IAM service</a> to sign data.
   */
  static class IAMCryptoSigner implements CryptoSigner {

    private static final String IAM_SIGN_BLOB_URL =
        "https://iam.googleapis.com/v1/projects/-/serviceAccounts/%s:signBlob";

    private final HttpRequestFactory requestFactory;
    private final JsonFactory jsonFactory;
    private final String serviceAccount;
    private HttpResponseInterceptor interceptor;

    IAMCryptoSigner(
        @NonNull HttpRequestFactory requestFactory,
        @NonNull JsonFactory jsonFactory,
        @NonNull String serviceAccount) {
      this.requestFactory = checkNotNull(requestFactory);
      this.jsonFactory = checkNotNull(jsonFactory);
      checkArgument(!Strings.isNullOrEmpty(serviceAccount));
      this.serviceAccount = serviceAccount;
    }

    void setInterceptor(HttpResponseInterceptor interceptor) {
      this.interceptor = interceptor;
    }

    @Override
    public byte[] sign(byte[] payload) throws IOException {
      String encodedUrl = String.format(IAM_SIGN_BLOB_URL, serviceAccount);
      HttpResponse response = null;
      String encodedPayload = BaseEncoding.base64().encode(payload);
      Map<String, String> content = ImmutableMap.of("bytesToSign", encodedPayload);
      try {
        HttpRequest request = requestFactory.buildPostRequest(new GenericUrl(encodedUrl),
            new JsonHttpContent(jsonFactory, content));
        request.setParser(new JsonObjectParser(jsonFactory));
        request.setResponseInterceptor(interceptor);
        response = request.execute();
        SignBlobResponse parsed = response.parseAs(SignBlobResponse.class);
        return BaseEncoding.base64().decode(parsed.signature);
      } finally {
        if (response != null) {
          try {
            response.disconnect();
          } catch (IOException ignored) {
            // Ignored
          }
        }
      }
    }

    @Override
    public String getAccount() {
      return serviceAccount;
    }
  }

  public static class SignBlobResponse {
    @Key("signature")
    private String signature;
  }

  /**
   * Initializes a {@link CryptoSigner} instance for the given Firebase app. Follows the protocol
   * documented at go/firebase-admin-sign.
   */
  static CryptoSigner getCryptoSigner(FirebaseApp firebaseApp) throws IOException {
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);

    // If the SDK was initialized with a service account, use it to sign bytes.
    if (credentials instanceof ServiceAccountCredentials) {
      return new ServiceAccountCryptoSigner((ServiceAccountCredentials) credentials);
    }

    FirebaseOptions options = firebaseApp.getOptions();
    HttpRequestFactory requestFactory = options.getHttpTransport().createRequestFactory(
        new FirebaseRequestInitializer(firebaseApp));
    JsonFactory jsonFactory = options.getJsonFactory();

    // If the SDK was initialized with a service account email, use it with the IAM service
    // to sign bytes.
    String serviceAccountId = options.getServiceAccountId();
    if (!Strings.isNullOrEmpty(serviceAccountId)) {
      return new IAMCryptoSigner(requestFactory, jsonFactory, serviceAccountId);
    }

    // If the SDK was initialized with some other credential type that supports signing
    // (e.g. GAE credentials), use it to sign bytes.
    if (credentials instanceof ServiceAccountSigner) {
      return new ServiceAccountCryptoSigner((ServiceAccountSigner) credentials);
    }

    // Attempt to discover a service account email from the local Metadata service. Use it
    // with the IAM service to sign bytes.
    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(METADATA_SERVICE_URL));
    request.getHeaders().set("Metadata-Flavor", "Google");
    HttpResponse response = request.execute();
    try {
      byte[] output = ByteStreams.toByteArray(response.getContent());
      serviceAccountId = StringUtils.newStringUtf8(output).trim();
      return new IAMCryptoSigner(requestFactory, jsonFactory, serviceAccountId);
    } finally {
      response.disconnect();
    }
  }
}
