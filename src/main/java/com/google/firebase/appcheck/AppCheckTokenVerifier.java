/*
 * Copyright  2022 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

package com.google.firebase.appcheck;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;

final class AppCheckTokenVerifier {

  private final URL jwksUrl;
  private final String projectId;

  private static final String JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
  private static final String APP_CHECK_ISSUER = "https://firebaseappcheck.googleapis.com/";

  private AppCheckTokenVerifier(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.projectId));
    this.projectId = builder.projectId;
    try {
      this.jwksUrl = new URL(JWKS_URL);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed JWK url string", e);
    }
  }

  /**
   * Verifies that the given App Check token string is a valid Firebase JWT.
   *
   * @param token The token string to be verified.
   * @return A decoded representation of the input token string.
   * @throws FirebaseAppCheckException If the input token string fails to verify due to any reason.
   */
  DecodedAppCheckToken verifyToken(String token) throws FirebaseAppCheckException {
    SignedJWT signedJWT;
    JWTClaimsSet claimsSet;
    String scopedProjectId = String.format("projects/%s", projectId);
    String projectIdMatchMessage = " Make sure the App Check token comes from the same "
            + "Firebase project as the service account used to authenticate this SDK.";

    try {
      signedJWT = SignedJWT.parse(token);
      claimsSet =  signedJWT.getJWTClaimsSet();
    } catch (java.text.ParseException e) {
      // Invalid signed JWT encoding
      throw new FirebaseAppCheckException(ErrorCode.INVALID_ARGUMENT, "Invalid token");
    }

    String errorMessage = null;

    if (!signedJWT.getHeader().getAlgorithm().equals(JWSAlgorithm.RS256)) {
      errorMessage = String.format("The provided App Check token has incorrect algorithm. "
              + "Expected 'RS256' but got '%s'.", signedJWT.getHeader().getAlgorithm());
    } else if (!signedJWT.getHeader().getType().getType().equals("JWT")) {
      errorMessage = String.format("The provided App Check token has invalid type header."
              + "Expected %s but got %s", "JWT", signedJWT.getHeader().getType().getType());
    } else if (!claimsSet.getAudience().contains(scopedProjectId)) {
      errorMessage = String.format("The provided App Check token has incorrect 'aud' (audience) "
              + "claim. Expected %s but got %s. %s",
              scopedProjectId, claimsSet.getAudience().toString(), projectIdMatchMessage);
    } else if (!claimsSet.getIssuer().startsWith(APP_CHECK_ISSUER)) {
      errorMessage = "invalid iss";
    } else if (claimsSet.getSubject().isEmpty()) {
      errorMessage = "invalid sub";
    }

    if (errorMessage != null) {
      throw new FirebaseAppCheckException(ErrorCode.INVALID_ARGUMENT, errorMessage);
    }

    // Create a JWT processor for the access tokens
    ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();

    // Cache the keys for 6 hours
    JWKSetCache jwkSetCache = new DefaultJWKSetCache(6L, 6L, TimeUnit.HOURS);
    JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(this.jwksUrl, null, jwkSetCache);

    // Configure the JWT processor with a key selector to feed matching public
    // RSA keys sourced from the JWK set URL.
    JWSKeySelector<SecurityContext> keySelector =
            new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

    jwtProcessor.setJWSKeySelector(keySelector);

    try {
      claimsSet = jwtProcessor.process(token, null);
      System.out.println(claimsSet.toJSONObject());
    } catch (ParseException | BadJOSEException | JOSEException e) {
      throw new FirebaseAppCheckException(ErrorCode.INVALID_ARGUMENT, e.getMessage());
    }

    return new DecodedAppCheckToken(claimsSet.getClaims());
  }

  static AppCheckTokenVerifier.Builder builder() {
    return new AppCheckTokenVerifier.Builder();
  }

  static final class Builder {

    private String projectId;

    private Builder() {
    }

    AppCheckTokenVerifier.Builder setProjectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    AppCheckTokenVerifier build() {
      return new AppCheckTokenVerifier(this);
    }
  }
}
