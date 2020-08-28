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
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.util.ArrayMap;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.firebase.ErrorCode;
import com.google.firebase.internal.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.List;

/**
 * The default implementation of the {@link FirebaseTokenVerifier} interface. Uses the Google API
 * client's {@code IdToken} API to decode and verify token strings. Can be customized to verify
 * both Firebase ID tokens and session cookies.
 */
final class FirebaseTokenVerifierImpl implements FirebaseTokenVerifier {

  private static final String RS256 = "RS256";
  private static final String FIREBASE_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";

  private final JsonFactory jsonFactory;
  private final GooglePublicKeysManager publicKeysManager;
  private final IdTokenVerifier idTokenVerifier;
  private final String method;
  private final String shortName;
  private final String articledShortName;
  private final String docUrl;
  private final AuthErrorCode invalidTokenErrorCode;
  private final AuthErrorCode expiredTokenErrorCode;
  private final String tenantId;

  private FirebaseTokenVerifierImpl(Builder builder) {
    this.jsonFactory = checkNotNull(builder.jsonFactory);
    this.publicKeysManager = checkNotNull(builder.publicKeysManager);
    this.idTokenVerifier = checkNotNull(builder.idTokenVerifier);
    checkArgument(!Strings.isNullOrEmpty(builder.method), "method name must be specified");
    checkArgument(!Strings.isNullOrEmpty(builder.shortName), "shortName must be specified");
    checkArgument(!Strings.isNullOrEmpty(builder.docUrl), "docUrl must be specified");
    this.method = builder.method;
    this.shortName = builder.shortName;
    this.articledShortName = prefixWithIndefiniteArticle(this.shortName);
    this.docUrl = builder.docUrl;
    this.invalidTokenErrorCode = checkNotNull(builder.invalidTokenErrorCode);
    this.expiredTokenErrorCode = checkNotNull(builder.expiredTokenErrorCode);
    this.tenantId = builder.tenantId;
  }

  /**
   * Verifies that the given token string is a valid Firebase JWT. This implementation considers
   * a token string to be valid if all the following conditions are met:
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
   *     listed above.
   */
  @Override
  public FirebaseToken verifyToken(String token) throws FirebaseAuthException {
    IdToken idToken = parse(token);
    checkContents(idToken);
    checkSignature(idToken);
    FirebaseToken firebaseToken = new FirebaseToken(idToken.getPayload());
    checkTenantId(firebaseToken);
    return firebaseToken;
  }

  GooglePublicKeysManager getPublicKeysManager() {
    return publicKeysManager;
  }

  IdTokenVerifier getIdTokenVerifier() {
    return idTokenVerifier;
  }

  String getMethod() {
    return method;
  }

  String getShortName() {
    return shortName;
  }

  String getArticledShortName() {
    return articledShortName;
  }

  String getDocUrl() {
    return docUrl;
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
          "Failed to parse Firebase %s. Make sure you passed a string that represents a complete "
              + "and valid JWT. See %s for details on how to retrieve %s.",
          shortName,
          docUrl,
          articledShortName);
      throw newException(detailedError, invalidTokenErrorCode, e);
    }
  }

  private void checkSignature(IdToken token) throws FirebaseAuthException {
    if (!isSignatureValid(token)) {
      String message = String.format(
          "Failed to verify the signature of Firebase %s. %s",
          shortName,
          getVerifyTokenMessage());
      throw newException(message, invalidTokenErrorCode);
    }
  }

  private void checkContents(final IdToken idToken) throws FirebaseAuthException {
    final Header header = idToken.getHeader();
    final Payload payload = idToken.getPayload();

    final long currentTimeMillis = idTokenVerifier.getClock().currentTimeMillis();
    String errorMessage = null;
    AuthErrorCode errorCode = invalidTokenErrorCode;

    if (header.getKeyId() == null) {
      errorMessage = getErrorForTokenWithoutKid(header, payload);
    } else if (!RS256.equals(header.getAlgorithm())) {
      errorMessage = String.format(
          "Firebase %s has incorrect algorithm. Expected \"%s\" but got \"%s\".",
          shortName,
          RS256,
          header.getAlgorithm());
    } else if (!idToken.verifyAudience(idTokenVerifier.getAudience())) {
      errorMessage = String.format(
          "Firebase %s has incorrect \"aud\" (audience) claim. Expected \"%s\" but got \"%s\". %s",
          shortName,
          joinWithComma(idTokenVerifier.getAudience()),
          joinWithComma(payload.getAudienceAsList()),
          getProjectIdMatchMessage());
    } else if (!idToken.verifyIssuer(idTokenVerifier.getIssuers())) {
      errorMessage = String.format(
          "Firebase %s has incorrect \"iss\" (issuer) claim. Expected \"%s\" but got \"%s\". %s",
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

  /**
   * Verifies the cryptographic signature on the FirebaseToken. Can block on a web request to fetch
   * the keys if they have expired.
   */
  private boolean isSignatureValid(IdToken token) throws FirebaseAuthException {
    for (PublicKey key : fetchPublicKeys()) {
      if (isSignatureValid(token, key)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSignatureValid(IdToken token, PublicKey key) throws FirebaseAuthException {
    try {
      return token.verifySignature(key);
    } catch (GeneralSecurityException e) {
      // This doesn't happen under usual circumstances. Seems to only happen if the crypto
      // setup of the runtime is incorrect in some way.
      throw new FirebaseAuthException(
          ErrorCode.UNKNOWN,
          String.format("Unexpected error while verifying %s: %s", shortName, e.getMessage()),
          e,
          null,
          invalidTokenErrorCode);
    }
  }

  private List<PublicKey> fetchPublicKeys() throws FirebaseAuthException {
    try {
      return publicKeysManager.getPublicKeys();
    } catch (GeneralSecurityException | IOException e) {
      throw new FirebaseAuthException(
          ErrorCode.UNKNOWN,
          "Error while fetching public key certificates: " + e.getMessage(),
          e,
          null,
          AuthErrorCode.CERTIFICATE_FETCH_FAILED);
    }
  }

  private String getErrorForTokenWithoutKid(IdToken.Header header, IdToken.Payload payload) {
    if (isCustomToken(payload)) {
      return String.format("%s expects %s, but was given a custom token.",
          method, articledShortName);
    } else if (isLegacyCustomToken(header, payload)) {
      return String.format("%s expects %s, but was given a legacy custom token.",
          method, articledShortName);
    }
    return String.format("Firebase %s has no \"kid\" claim.", shortName);
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

  private boolean isCustomToken(IdToken.Payload payload) {
    return FIREBASE_AUDIENCE.equals(payload.getAudience());
  }

  private boolean isLegacyCustomToken(IdToken.Header header, IdToken.Payload payload) {
    return "HS256".equals(header.getAlgorithm())
        && new BigDecimal(0).equals(payload.get("v"))
        && containsLegacyUidField(payload);
  }

  private boolean containsLegacyUidField(IdToken.Payload payload) {
    Object dataField = payload.get("d");
    if (dataField instanceof ArrayMap) {
      return ((ArrayMap) dataField).get("uid") != null;
    }
    return false;
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

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {

    private JsonFactory jsonFactory;
    private GooglePublicKeysManager publicKeysManager;
    private String method;
    private String shortName;
    private IdTokenVerifier idTokenVerifier;
    private String docUrl;
    private AuthErrorCode invalidTokenErrorCode;
    private AuthErrorCode expiredTokenErrorCode;
    private String tenantId;

    private Builder() { }

    Builder setJsonFactory(JsonFactory jsonFactory) {
      this.jsonFactory = jsonFactory;
      return this;
    }

    Builder setPublicKeysManager(GooglePublicKeysManager publicKeysManager) {
      this.publicKeysManager = publicKeysManager;
      return this;
    }

    Builder setMethod(String method) {
      this.method = method;
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

    FirebaseTokenVerifierImpl build() {
      return new FirebaseTokenVerifierImpl(this);
    }
  }
}
