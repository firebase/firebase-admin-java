/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.appcheck.AppCheckErrorCode;
import com.google.firebase.appcheck.DecodedAppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheckException;
import com.google.firebase.appcheck.VerifyAppCheckTokenOptions;
import com.google.firebase.appcheck.VerifyAppCheckTokenResponse;
import com.google.firebase.internal.AbstractPlatformErrorHandler;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.ErrorHandlingHttpClient;
import com.google.firebase.internal.HttpRequestInfo;
import com.google.firebase.internal.SdkUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.ExpiredJWTException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Internal verifier for Firebase App Check tokens.
 */
public class AppCheckTokenVerifier {

  private static final String JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
  private static final String APP_CHECK_ISSUER = "https://firebaseappcheck.googleapis.com/";
  private static final String APP_CHECK_AUDIENCE_PREFIX = "projects/";
  private static final String VERIFY_TOKEN_URL_FORMAT =
      "https://firebaseappcheck.googleapis.com/v1beta/projects/%s:verifyAppCheckToken";
  private static final long JWKS_CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(6);
  private static final Map<String, String> COMMON_HEADERS =
      ImmutableMap.of("X-Firebase-Client", "fire-admin-java/" + SdkUtils.getVersion());

  private final FirebaseApp app;
  private final String projectId;
  private final ErrorHandlingHttpClient<FirebaseAppCheckException> httpClient;
  private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

  public AppCheckTokenVerifier(FirebaseApp app) {
    this(
        app,
        ApiClientUtils.newAuthorizedRequestFactory(app),
        ApiClientUtils.getDefaultJsonFactory(),
        null);
  }

  @VisibleForTesting
  AppCheckTokenVerifier(
      FirebaseApp app,
      HttpRequestFactory requestFactory,
      JsonFactory jsonFactory,
      DefaultJWTProcessor<SecurityContext> jwtProcessor) {
    this.app = checkNotNull(app, "FirebaseApp must not be null");
    this.projectId = getProjectId(app);
    checkNotNull(requestFactory, "HttpRequestFactory must not be null");
    checkNotNull(jsonFactory, "JsonFactory must not be null");
    this.httpClient = new ErrorHandlingHttpClient<>(
        requestFactory, jsonFactory, new AppCheckErrorHandler(jsonFactory));
    this.jwtProcessor = jwtProcessor != null ? jwtProcessor : createJwtProcessor();
  }

  /**
   * Verifies an App Check token string.
   *
   * @param token The App Check token string to verify.
   * @return A {@link VerifyAppCheckTokenResponse} containing the decoded token.
   * @throws FirebaseAppCheckException If verification fails.
   */
  public VerifyAppCheckTokenResponse verifyToken(String token) throws FirebaseAppCheckException {
    return verifyToken(token, null);
  }

  /**
   * Verifies an App Check token string with options.
   *
   * @param token The App Check token string to verify.
   * @param options Verification options specified via {@link VerifyAppCheckTokenOptions}.
   * @return A {@link VerifyAppCheckTokenResponse} containing the decoded token
   *     and consumption status.
   * @throws FirebaseAppCheckException If verification fails.
   */
  public VerifyAppCheckTokenResponse verifyToken(
      String token, VerifyAppCheckTokenOptions options) throws FirebaseAppCheckException {
    checkArgument(
        !Strings.isNullOrEmpty(token), "App Check token string must not be null or empty");

    DecodedAppCheckToken decodedToken = verifyTokenLocally(token);
    String appId = decodedToken.getSubject();

    boolean consume = options != null && options.getConsume().orElse(false);
    Boolean alreadyConsumed = null;

    if (consume) {
      alreadyConsumed = verifyOneTimeToken(token);
    }

    return new VerifyAppCheckTokenResponse(appId, decodedToken, alreadyConsumed);
  }

  /**
   * Performs local JWT signature and claims verification.
   */
  private DecodedAppCheckToken verifyTokenLocally(String token) throws FirebaseAppCheckException {
    try {
      SignedJWT signedJwt = SignedJWT.parse(token);
      verifyHeader(signedJwt.getHeader());
      JWTClaimsSet claims = this.jwtProcessor.process(signedJwt, null);
      verifyClaims(claims);
      return new DecodedAppCheckToken(claims.getClaims());
    } catch (ParseException e) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "Failed to parse App Check JWT token: " + e.getMessage(),
          e,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    } catch (ExpiredJWTException e) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "Firebase App Check token has expired.",
          e,
          null,
          AppCheckErrorCode.APP_CHECK_TOKEN_EXPIRED);
    } catch (BadJOSEException e) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "Check your project: " + projectId + ". Failed to verify App Check token signature: "
              + e.getMessage(),
          e,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    } catch (JOSEException e) {
      throw new FirebaseAppCheckException(
          ErrorCode.INTERNAL,
          "Check your project: " + projectId + ". Internal error processing App Check token: "
              + e.getMessage(),
          e,
          null,
          AppCheckErrorCode.INTERNAL_ERROR);
    }
  }

  private void verifyHeader(JWSHeader header) throws FirebaseAppCheckException {
    if (header.getAlgorithm() == null || !JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has incorrect algorithm. Expected "
              + JWSAlgorithm.RS256.getName()
              + " but got: "
              + header.getAlgorithm(),
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }
    if (Strings.isNullOrEmpty(header.getKeyID())) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has no 'kid' (key ID) header.",
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }
    if (header.getType() == null || !JOSEObjectType.JWT.equals(header.getType())) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has incorrect 'typ' header. Expected JWT but got: "
              + header.getType(),
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }
  }

  private void verifyClaims(JWTClaimsSet claims) throws FirebaseAppCheckException {
    checkNotNull(claims, "JWTClaimsSet claims must not be null");
    String issuer = claims.getIssuer();

    if (Strings.isNullOrEmpty(issuer)) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has no 'iss' (issuer) claim.",
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }

    // The issuer is of the form https://firebaseappcheck.googleapis.com/<project_number>.
    // Because the SDK currently only has access to the project ID (not the project number),
    // the verifier checks the issuer prefix rather than strict equality. If the SDK is updated in
    // the future to expose the project number (per https://google.aip.dev/cloud/2510), this check
    // should be updated to verify strict equality against
    // https://firebaseappcheck.googleapis.com/<project_number>.
    if (!issuer.startsWith(APP_CHECK_ISSUER)) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has incorrect issuer. Expected to start with: "
              + APP_CHECK_ISSUER
              + " but got: "
              + issuer,
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }

    List<String> audience = claims.getAudience();
    String expectedAudience = APP_CHECK_AUDIENCE_PREFIX + this.projectId;
    if (audience == null || audience.isEmpty() || !audience.contains(expectedAudience)) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has incorrect audience. Expected to contain: "
              + expectedAudience
              + " but got: "
              + audience,
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }

    if (Strings.isNullOrEmpty(claims.getSubject())) {
      throw new FirebaseAppCheckException(
          ErrorCode.INVALID_ARGUMENT,
          "App Check token has empty 'sub' (app ID) claim.",
          null,
          null,
          AppCheckErrorCode.INVALID_APP_CHECK_TOKEN);
    }
  }

  /**
   * Sends an RPC request to the Firebase App Check backend service to verify
   * and consume a limited-use (one-time) App Check token.
   *
   * @param token The raw App Check token string to consume.
   * @return {@code true} if the token was already consumed prior to this verification call;
   *         {@code false} otherwise.
   * @throws FirebaseAppCheckException If an HTTP error, backend service failure, or unexpected
   *     response occurs.
   */
  private boolean verifyOneTimeToken(String token)
      throws FirebaseAppCheckException {
    HttpRequestInfo request =
        HttpRequestInfo.buildJsonPostRequest(
                String.format(VERIFY_TOKEN_URL_FORMAT, this.projectId),
                ImmutableMap.of("app_check_token", token))
            .addAllHeaders(COMMON_HEADERS);
    IncomingHttpResponse response = httpClient.send(request);
    if (Strings.isNullOrEmpty(response.getContent())) {
      throw unexpectedResponse("Response body is empty.", response, null);
    }

    GenericJson body;
    try {
      body = httpClient.parse(response, GenericJson.class);
    } catch (IllegalArgumentException e) {
      // Thrown for valid JSON that is not an object (e.g. an array or a string).
      throw unexpectedResponse("Expected a JSON object.", response, e);
    }

    Object alreadyConsumed = body.get("alreadyConsumed");
    if (alreadyConsumed == null) {
      return false;
    }
    if (!(alreadyConsumed instanceof Boolean)) {
      throw unexpectedResponse("'alreadyConsumed' must be a boolean.", response, null);
    }
    return (Boolean) alreadyConsumed;
  }

  private static FirebaseAppCheckException unexpectedResponse(
      String detail, IncomingHttpResponse response, Throwable cause) {
    return new FirebaseAppCheckException(
        ErrorCode.UNKNOWN,
        "Unexpected response from App Check service. " + detail,
        cause,
        response);
  }

  private DefaultJWTProcessor<SecurityContext> createJwtProcessor() {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    try {
      JWKSource<SecurityContext> keySource = createKeySource();
      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
      processor.setJWSKeySelector(keySelector);
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid JWKS URL", e);
    }
    return processor;
  }

  protected JWKSource<SecurityContext> createKeySource() throws MalformedURLException {
    return JWKSourceBuilder.create(URI.create(JWKS_URL).toURL())
        .cache(JWKS_CACHE_TTL_MILLIS, JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
        .retrying(true)
        .build();
  }

  private String getProjectId(FirebaseApp app) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    if (Strings.isNullOrEmpty(projectId)) {
      throw new IllegalArgumentException("Project ID is required in FirebaseOptions.");
    }
    return projectId;
  }

  private static class AppCheckErrorHandler
      extends AbstractPlatformErrorHandler<FirebaseAppCheckException> {

    private AppCheckErrorHandler(JsonFactory jsonFactory) {
      super(jsonFactory);
    }

    @Override
    protected FirebaseAppCheckException createException(FirebaseException base) {
      return new FirebaseAppCheckException(base);
    }
  }
}
