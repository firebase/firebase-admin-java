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

package com.google.firebase.fpnv.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.fpnv.FirebasePnvErrorCode;
import com.google.firebase.fpnv.FirebasePnvException;
import com.google.firebase.fpnv.FirebasePnvToken;
import com.nimbusds.jose.JOSEException;
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
import java.net.URL;
import java.text.ParseException;
import java.util.Objects;

/**
 * Internal class to verify FPNV tokens.
 */
public class FirebasePnvTokenVerifier {
  private static final String FPNV_JWKS_URL = "https://fpnv.googleapis.com/v1beta/jwks";
  private static final String HEADER_TYP = "JWT";

  private final String projectId;
  private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

  /**
   * Create {@link FirebasePnvTokenVerifier} for internal purposes.
   *
   * @param app The {@link FirebaseApp} to get a FirebaseAuth instance for.
   */
  public FirebasePnvTokenVerifier(FirebaseApp app) {
    this.projectId = getProjectId(app);
    this.jwtProcessor = createJwtProcessor();
  }

  /**
   * Main method that performs the following verification steps:
   * - Explicitly verifies the header
   * - Verifies signature and structure
   * - Verifies claims (e.g. issuer, audience, expiration)
   * - Constructs a token object upon successful verification
   *
   * @param token String input data
   * @return {@link FirebasePnvToken}
   * @throws FirebasePnvException Can throw {@link FirebasePnvException}
   */
  public FirebasePnvToken verifyToken(String token) throws FirebasePnvException {
    checkArgument(!Strings.isNullOrEmpty(token), "FPNV token must not be null or empty");

    try {
      // Parse the token first to inspect header
      SignedJWT signedJwt = SignedJWT.parse(token);

      // Explicitly verify the header (alg & kid)
      verifyHeader(signedJwt.getHeader());

      // Verify Signature and Structure
      JWTClaimsSet claims = jwtProcessor.process(signedJwt, null);

      // Verify Claims (Issuer, Audience, Expiration)
      verifyClaims(claims);

      // Construct Token Object
      return new FirebasePnvToken(claims.getClaims());
    } catch (ParseException e) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_TOKEN,
          "Failed to parse JWT token: " + e.getMessage(),
          e
      );
    } catch (ExpiredJWTException e) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.TOKEN_EXPIRED,
          "FPNV token has expired.",
          e
      );
    } catch (BadJOSEException e) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_TOKEN,
          "Check your project: " + projectId + ". "
          + "FPNV token is invalid: " + e.getMessage(),
          e
      );
    } catch (JOSEException e) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INTERNAL_ERROR,
          "Check your project: " + projectId + ". "
          + "Failed to verify FPNV token signature: " + e.getMessage(),
          e
      );
    }
  }

  private void verifyHeader(JWSHeader header) throws FirebasePnvException {
    // Check Algorithm (alg)
    if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_ARGUMENT,
          "FPNV has incorrect 'algorithm'. Expected " + JWSAlgorithm.ES256.getName()
              + " but got " + header.getAlgorithm());
    }
    // Check Key ID (kid)
    if (Strings.isNullOrEmpty(header.getKeyID())) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_ARGUMENT,
          "FPNV has no 'kid' claim."
      );
    }
    // Check Typ (typ)
    if (Objects.isNull(header.getType()) || !HEADER_TYP.equals(header.getType().getType())) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_ARGUMENT,
          "FPNV has incorrect 'typ'. Expected " + HEADER_TYP
              + " but got " + header.getType()
      );
    }

  }

  private void verifyClaims(JWTClaimsSet claims) throws FirebasePnvException {
    checkArgument(!Objects.isNull(claims), "JWTClaimsSet claims must not be null");
    // Verify Issuer
    String issuer = claims.getIssuer();

    if (Strings.isNullOrEmpty(issuer)) {
      throw new FirebasePnvException(FirebasePnvErrorCode.INVALID_ARGUMENT,
          "FPNV token has no 'iss' (issuer) claim.");
    }

    // Verify Audience
    if (claims.getAudience() == null
        || claims.getAudience().isEmpty()
        || !claims.getAudience().contains(issuer)
    ) {
      throw new FirebasePnvException(FirebasePnvErrorCode.INVALID_TOKEN,
          "Invalid audience. Expected to contain: "
              + issuer + " but found: " + claims.getAudience()
      );
    }

    // Verify Subject for emptiness / null
    if (Strings.isNullOrEmpty(claims.getSubject())) {
      throw new FirebasePnvException(
          FirebasePnvErrorCode.INVALID_TOKEN,
          "Token has an empty 'sub' (phone number)."
      );
    }
  }

  private DefaultJWTProcessor<SecurityContext> createJwtProcessor() {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    try {
      // Use JWKSourceBuilder instead of deprecated RemoteJWKSet
      JWKSource<SecurityContext> keySource = JWKSourceBuilder
          .create(new URL(FPNV_JWKS_URL))
          .retrying(true) // Helper to retry on transient network errors
          .build();

      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, keySource);
      processor.setJWSKeySelector(keySelector);
    } catch (MalformedURLException e) {
      throw new RuntimeException("Invalid JWKS URL", e);
    }
    return processor;
  }

  private String getProjectId(FirebaseApp app) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    if (Strings.isNullOrEmpty(projectId)) {
      throw new IllegalArgumentException("Project ID is required in FirebaseOptions.");
    }
    return projectId;
  }
}
