/*
 * Copyright  2019 Google Inc.
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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.google.firebase.internal.Nullable;
import java.io.IOException;

/**
 * The implementation of the {@link FirebaseTokenVerifier} interface, for the Auth Emulator. Can be
 * customized to verify both Firebase ID tokens and session cookies.
 */
final class EmulatorFirebaseTokenVerifier implements FirebaseTokenVerifier {

  private final JsonFactory jsonFactory;
  private final IdTokenVerifier idTokenVerifier;
  private final String shortName;
  private final String articledShortName;
  private final String docUrl;
  private final AuthErrorCode invalidTokenErrorCode;
  private final AuthErrorCode expiredTokenErrorCode;
  private final String tenantId;

  private EmulatorFirebaseTokenVerifier(Builder builder) {
    this.jsonFactory = checkNotNull(builder.jsonFactory);
    this.idTokenVerifier = checkNotNull(builder.idTokenVerifier);
    checkArgument(!Strings.isNullOrEmpty(builder.shortName), "shortName must be specified");
    checkArgument(!Strings.isNullOrEmpty(builder.docUrl), "docUrl must be specified");
    this.shortName = builder.shortName;
    this.articledShortName = prefixWithIndefiniteArticle(this.shortName);
    this.docUrl = builder.docUrl;
    this.invalidTokenErrorCode = checkNotNull(builder.invalidTokenErrorCode);
    this.expiredTokenErrorCode = checkNotNull(builder.expiredTokenErrorCode);
    this.tenantId = builder.tenantId;
  }

  static Builder builder() {
    return new Builder();
  }

  /**
   * Verifies that the given token string is a valid Firebase JWT. This implementation considers a
   * token string to be valid if all the following conditions are met:
   * <ol>
   *   <li>The token string is a valid RS256 JWT.</li>
   *   <li>The JWT contains a valid key ID (kid) claim.</li>
   *   <li>The JWT is not expired, and it has been issued some time in the past.</li>
   *   <li>The JWT contains valid issuer (iss) and audience (aud) claims as determined by the
   *   {@code IdTokenVerifier}.</li>
   *   <li>The JWT contains a valid subject (sub) claim.</li>
   *   <li>The JWT is signed by a Firebase Auth backend server.</li>
   * </ol>
   *
   * @param token The token string to be verified.
   * @return A decoded representation of the input token string.
   * @throws FirebaseAuthException If the input token string does not meet any of the conditions
   *         listed above.
   */
  @Override
  public FirebaseToken verifyToken(String token) throws FirebaseAuthException {
    IdToken idToken = parse(token);
    checkContents(idToken);
    FirebaseToken firebaseToken = new FirebaseToken(idToken.getPayload());
    checkTenantId(firebaseToken);
    return firebaseToken;
  }

  private String prefixWithIndefiniteArticle(String word) {
    if ("aeiouAEIOU".indexOf(word.charAt(0)) < 0) {
      return "a " + word;
    } else {
      return "an " + word;
    }
  }

  private IdToken parse(String token) throws FirebaseAuthException {
    try {
      return IdToken.parse(jsonFactory, token);
    } catch (IllegalArgumentException | IOException e) {
      // Old versions of guava throw an IOException for invalid strings, while new versions
      // might throw an IllegalArgumentException
      String detailedError = String.format(
              "Failed to parse Firebase %s. Make sure you passed a string that represents "
                      + "a complete and valid JWT. See %s for details on how to retrieve %s.",
              shortName,
              docUrl,
              articledShortName);
      throw newException(detailedError, invalidTokenErrorCode, e);
    }
  }

  private void checkContents(final IdToken idToken) throws FirebaseAuthException {
    final Payload payload = idToken.getPayload();

    final long currentTimeMillis = idTokenVerifier.getClock().currentTimeMillis();
    String errorMessage = null;
    AuthErrorCode errorCode = invalidTokenErrorCode;

    if (!idToken.verifyAudience(idTokenVerifier.getAudience())) {
      errorMessage = String.format(
              "Firebase %s has incorrect \"aud\" (audience) claim. "
                      + "Expected \"%s\" but got \"%s\". %s",
              shortName,
              joinWithComma(idTokenVerifier.getAudience()),
              joinWithComma(payload.getAudienceAsList()),
              getProjectIdMatchMessage());
    } else if (!idToken.verifyIssuer(idTokenVerifier.getIssuers())) {
      errorMessage = String.format(
              "Firebase %s has incorrect \"iss\" (issuer) claim. "
                      + "Expected \"%s\" but got \"%s\". %s",
              shortName,
              joinWithComma(idTokenVerifier.getIssuers()),
              payload.getIssuer(),
              getProjectIdMatchMessage());
    } else if (payload.getSubject() == null) {
      errorMessage = String.format(
              "Firebase %s has no \"sub\" (subject) claim.",
              shortName);
    } else if (payload.getSubject().isEmpty()) {
      errorMessage = String.format(
              "Firebase %s has an empty string \"sub\" (subject) claim.",
              shortName);
    } else if (payload.getSubject().length() > 128) {
      errorMessage = String.format(
              "Firebase %s has \"sub\" (subject) claim longer than 128 characters.",
              shortName);
    } else if (!idToken.verifyExpirationTime(
            currentTimeMillis, idTokenVerifier.getAcceptableTimeSkewSeconds())) {
      errorMessage = String.format(
              "Firebase %s has expired. Get a fresh %s and try again.",
              shortName,
              shortName);
      // Also set the expired error code.
      errorCode = expiredTokenErrorCode;
    } else if (!idToken.verifyIssuedAtTime(
            currentTimeMillis, idTokenVerifier.getAcceptableTimeSkewSeconds())) {
      errorMessage = String.format(
              "Firebase %s is not yet valid.",
              shortName);
    }

    if (errorMessage != null) {
      String detailedError = String.format("%s %s", errorMessage, getVerifyTokenMessage());
      throw newException(detailedError, errorCode);
    }
  }

  private FirebaseAuthException newException(String message, AuthErrorCode errorCode) {
    return newException(message, errorCode, null);
  }

  private FirebaseAuthException newException(
          String message, AuthErrorCode errorCode, Throwable cause) {
    return new FirebaseAuthException(
            ErrorCode.INVALID_ARGUMENT, message, cause, null, errorCode);
  }

  private String getVerifyTokenMessage() {
    return String.format(
            "See %s for details on how to retrieve %s.",
            docUrl,
            articledShortName);
  }

  private String joinWithComma(Iterable<String> strings) {
    return Joiner.on(',').join(strings);
  }

  private String getProjectIdMatchMessage() {
    return String.format(
            "Make sure the %s comes from the same Firebase project as the service account used to "
                    + "authenticate this SDK.",
            shortName);
  }

  private void checkTenantId(final FirebaseToken firebaseToken) throws FirebaseAuthException {
    String tokenTenantId = firebaseToken.getTenantId();
    if (this.tenantId != null && !this.tenantId.equals(tokenTenantId)) {
      String message = String.format(
              "The tenant ID ('%s') of the token did not match the expected value ('%s')",
              Strings.nullToEmpty(tokenTenantId),
              tenantId);
      throw newException(message, AuthErrorCode.TENANT_ID_MISMATCH);
    }
  }

  static final class Builder {

    private JsonFactory jsonFactory;
    private String shortName;
    private IdTokenVerifier idTokenVerifier;
    private String docUrl;
    private AuthErrorCode invalidTokenErrorCode;
    private AuthErrorCode expiredTokenErrorCode;
    private String tenantId;

    private Builder() {
    }

    Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    Builder setShortName(String shortName) {
      this.shortName = shortName;
      return this;
    }

    Builder setIdTokenVerifier(IdTokenVerifier idTokenVerifier) {
      this.idTokenVerifier = idTokenVerifier;
      return this;
    }

    Builder setDocUrl(String docUrl) {
      this.docUrl = docUrl;
      return this;
    }

    Builder setInvalidTokenErrorCode(AuthErrorCode invalidTokenErrorCode) {
      this.invalidTokenErrorCode = invalidTokenErrorCode;
      return this;
    }

    Builder setExpiredTokenErrorCode(AuthErrorCode expiredTokenErrorCode) {
      this.expiredTokenErrorCode = expiredTokenErrorCode;
      return this;
    }

    Builder setTenantId(@Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    EmulatorFirebaseTokenVerifier build() {
      return new EmulatorFirebaseTokenVerifier(this);
    }
  }
}
