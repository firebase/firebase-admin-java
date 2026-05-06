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

package com.google.firebase.phonenumberverification.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.phonenumberverification.FirebasePhoneNumberVerificationErrorCode;
import com.google.firebase.phonenumberverification.FirebasePhoneNumberVerificationException;
import com.google.firebase.phonenumberverification.FirebasePhoneNumberVerificationToken;
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
import java.util.Objects;

/**
 * Internal class to verify Firebase Phone Number Verification tokens.
 */
public class FirebasePhoneNumberVerificationTokenVerifier {
  private static final String FPNV_JWKS_URL = "https://fpnv.googleapis.com/v1beta/jwks";
  private static final String HEADER_TYP = "JWT";

  private final String projectId;
  private DefaultJWTProcessor<SecurityContext> jwtProcessor;

  /**
   * Create {@link FirebasePhoneNumberVerificationTokenVerifier} for internal purposes.
   *
   * @param app The {@link FirebaseApp} to get a project ID from.
   */
  public FirebasePhoneNumberVerificationTokenVerifier(FirebaseApp app) {
    this.projectId = getProjectId(app);
  }

  /**
   * Package-private constructor designed explicitly for dependency injection
   * during isolated unit testing flows.
   */
  FirebasePhoneNumberVerificationTokenVerifier(
      String projectId, DefaultJWTProcessor<SecurityContext> jwtProcessor) {
    this.projectId = projectId;
    this.jwtProcessor = jwtProcessor;
  }

  private DefaultJWTProcessor<SecurityContext> getJwtProcessor() {
    DefaultJWTProcessor<SecurityContext> processor = this.jwtProcessor;
    if (processor == null) {
      synchronized (this) {
        processor = this.jwtProcessor;
        if (processor == null) {
          processor = createJwtProcessor();
          this.jwtProcessor = processor;
        }
      }
    }
    return processor;
  }

  /**
   * Main method that performs token verification steps.
   *
   * @param token String input data
   * @return {@link FirebasePhoneNumberVerificationToken}
   * @throws FirebasePhoneNumberVerificationException If verification fails
   */
  public FirebasePhoneNumberVerificationToken verifyToken(String token)
      throws FirebasePhoneNumberVerificationException {
    checkArgument(!Strings.isNullOrEmpty(token),
        "Firebase Phone Number Verification token must not be null or empty");

    try {
      SignedJWT signedJwt = SignedJWT.parse(token);
      verifyHeader(signedJwt.getHeader());

      JWTClaimsSet claims = getJwtProcessor().process(signedJwt, null);
      verifyClaims(claims);

      return new FirebasePhoneNumberVerificationToken(claims.getClaims());
    } catch (ParseException e) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
          "Failed to parse JWT token: " + e.getMessage(),
          e
      );
    } catch (ExpiredJWTException e) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.TOKEN_EXPIRED,
          "Firebase Phone Number Verification token has expired.",
          e
      );
    } catch (BadJOSEException e) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
          "Check your project: " + projectId + ". "
          + "Firebase Phone Number Verification token is invalid: "
          + e.getMessage(),
          e
      );
    } catch (JOSEException e) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INTERNAL_ERROR,
          "Check your project: " + projectId + ". Failed to verify "
          + "Firebase Phone Number Verification token signature: " + e.getMessage(),
          e
      );
    }
  }

  private void verifyHeader(JWSHeader header) throws FirebasePhoneNumberVerificationException {
    if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_ARGUMENT,
          "Firebase Phone Number Verification token has incorrect 'algorithm'. "
          + "Expected " + JWSAlgorithm.ES256.getName() + " but got " + header.getAlgorithm());
    }
    if (Strings.isNullOrEmpty(header.getKeyID())) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_ARGUMENT,
          "Firebase Phone Number Verification token has no 'kid' claim."
      );
    }
    if (!JOSEObjectType.JWT.equals(header.getType())) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_ARGUMENT,
          "Firebase Phone Number Verification token has incorrect 'typ'. Expected " + HEADER_TYP
              + " but got " + header.getType()
      );
    }
  }

  private void verifyClaims(JWTClaimsSet claims) throws FirebasePhoneNumberVerificationException {
    checkNotNull(claims, "JWTClaimsSet claims must not be null");
    String issuer = claims.getIssuer();

    if (Strings.isNullOrEmpty(issuer)) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_ARGUMENT,
          "Firebase Phone Number Verification token has no 'iss' (issuer) claim.");
    }

    String expectedIssuer = "https://fpnv.googleapis.com/projects/" + this.projectId;
    if (!expectedIssuer.equals(issuer)) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
          "Firebase Phone Number Verification token has an incorrect 'iss' (issuer) claim.");
    }

    if (claims.getAudience().isEmpty() || !claims.getAudience().contains(issuer)) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
          "Invalid audience. Expected to contain: " + issuer
          + " but found: " + claims.getAudience()
      );
    }

    if (Strings.isNullOrEmpty(claims.getSubject())) {
      throw new FirebasePhoneNumberVerificationException(
          FirebasePhoneNumberVerificationErrorCode.INVALID_TOKEN,
          "Token has an empty 'sub' (phone number)."
      );
    }
  }

  private DefaultJWTProcessor<SecurityContext> createJwtProcessor() {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    try {
      JWKSource<SecurityContext> keySource = createKeySource();
      JWSKeySelector<SecurityContext> keySelector =
          new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, keySource);
      processor.setJWSKeySelector(keySelector);
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid JWKS URL", e);
    }
    return processor;
  }

  protected JWKSource<SecurityContext> createKeySource() throws MalformedURLException {
    return JWKSourceBuilder
        .create(URI.create(FPNV_JWKS_URL).toURL())
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
}
