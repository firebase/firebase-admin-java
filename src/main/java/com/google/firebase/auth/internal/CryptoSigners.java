package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.StringUtils;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.internal.AbstractPlatformErrorHandler;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.NonNull;
import java.io.IOException;
import java.util.Map;

/**
 * A set of {@link CryptoSigner} implementations and utilities for interacting with them.
 */
public class CryptoSigners {

  private static final String METADATA_SERVICE_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email";

  private CryptoSigners() { }

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
   * <a href=https://cloud.google.com/iam/docs/reference/credentials/rest/v1/projects.serviceAccounts/signBlob">
   * Google IAMCredentials service</a> to sign data.
   */
  static class IAMCryptoSigner implements CryptoSigner {

    private static final String IAM_SIGN_BLOB_URL =
        "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/%s:signBlob";

    private final String serviceAccount;
    private final ErrorHandlingHttpClient<FirebaseAuthException> httpClient;

    IAMCryptoSigner(
        @NonNull HttpRequestFactory requestFactory,
        @NonNull JsonFactory jsonFactory,
        @NonNull String serviceAccount) {
      checkArgument(!Strings.isNullOrEmpty(serviceAccount));
      this.serviceAccount = serviceAccount;
      this.httpClient = new ErrorHandlingHttpClient<>(
          requestFactory,
          jsonFactory,
          new IAMErrorHandler(jsonFactory));
    }

    void setInterceptor(HttpResponseInterceptor interceptor) {
      httpClient.setInterceptor(interceptor);
    }

    @Override
    public byte[] sign(byte[] payload) throws FirebaseAuthException {
      String encodedPayload = BaseEncoding.base64().encode(payload);
      Map<String, String> content = ImmutableMap.of("payload", encodedPayload);
      String encodedUrl = String.format(IAM_SIGN_BLOB_URL, serviceAccount);
      HttpRequestInfo requestInfo = HttpRequestInfo.buildJsonPostRequest(encodedUrl, content);
      GenericJson parsed = httpClient.sendAndParse(requestInfo, GenericJson.class);
      return BaseEncoding.base64().decode((String) parsed.get("signedBlob"));
    }

    @Override
    public String getAccount() {
      return serviceAccount;
    }
  }

  private static class IAMErrorHandler
      extends AbstractPlatformErrorHandler<FirebaseAuthException> {

    IAMErrorHandler(JsonFactory jsonFactory) {
      super(jsonFactory);
    }

    @Override
    protected FirebaseAuthException createException(FirebaseException base) {
      return new FirebaseAuthException(base);
    }
  }

  /**
   * Initializes a {@link CryptoSigner} instance for the given Firebase app. Follows the protocol
   * documented at go/firebase-admin-sign.
   */
  public static CryptoSigner getCryptoSigner(FirebaseApp firebaseApp) throws IOException {
    GoogleCredentials credentials = ImplFirebaseTrampolines.getCredentials(firebaseApp);

    // If the SDK was initialized with a service account, use it to sign bytes.
    if (credentials instanceof ServiceAccountCredentials) {
      return new ServiceAccountCryptoSigner((ServiceAccountCredentials) credentials);
    }

    HttpRequestFactory requestFactory = ApiClientUtils.newAuthorizedRequestFactory(firebaseApp);
    JsonFactory jsonFactory = firebaseApp.getOptions().getJsonFactory();

    // If the SDK was initialized with a service account email, use it with the IAM service
    // to sign bytes.
    String serviceAccountId = firebaseApp.getOptions().getServiceAccountId();
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
    serviceAccountId = discoverServiceAccountId(firebaseApp);
    return new IAMCryptoSigner(requestFactory, jsonFactory, serviceAccountId);
  }

  private static String discoverServiceAccountId(FirebaseApp firebaseApp) throws IOException {
    HttpRequestFactory metadataRequestFactory =
        ApiClientUtils.newUnauthorizedRequestFactory(firebaseApp);
    HttpRequest request = metadataRequestFactory.buildGetRequest(
        new GenericUrl(METADATA_SERVICE_URL));
    request.getHeaders().set("Metadata-Flavor", "Google");
    HttpResponse response = request.execute();
    try {
      byte[] output = ByteStreams.toByteArray(response.getContent());
      return StringUtils.newStringUtf8(output).trim();
    } finally {
      ApiClientUtils.disconnectQuietly(response);
    }
  }
}
