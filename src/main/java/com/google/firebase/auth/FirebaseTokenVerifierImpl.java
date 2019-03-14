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
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * The default implementation of the {@link FirebaseTokenVerifier} interface. Uses the Google API
 * client's {@code IdToken} API to decode and verify token strings. Can be customized to verify
 * both Firebase ID tokens and session cookies.
 */
final class FirebaseTokenVerifierImpl implements FirebaseTokenVerifier {

  private static final String RS256 = "RS256";
  private static final String FIREBASE_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";
  private static final String ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL";
  private static final String ERROR_RUNTIME_EXCEPTION = "ERROR_RUNTIME_EXCEPTION";

  private final JsonFactory jsonFactory;
  private final GooglePublicKeysManager publicKeysManager;
  private final IdTokenVerifier idTokenVerifier;
  private final String method;
  private final String shortName;
  private final String articledShortName;
  private final String docUrl;

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
    return new FirebaseToken(idToken.getPayload());
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
    } catch (IOException e) {
      String detailedError = String.format(
          "Failed to parse Firebase %s. Make sure you passed a string that represents a complete "
              + "and valid JWT. See %s for details on how to retrieve %s.",
          shortName,
          docUrl,
          articledShortName);
      throw new FirebaseAuthException(ERROR_INVALID_CREDENTIAL, detailedError, e);
    }
  }

  private void checkContents(final IdToken token) throws FirebaseAuthException {
    String errorMessage = getErrorIfContentInvalid(token);
    if (errorMessage != null) {
      String detailedError = String.format("%s %s", errorMessage, getVerifyTokenMessage());
      throw new FirebaseAuthException(ERROR_INVALID_CREDENTIAL, detailedError);
    }
  }

  private void checkSignature(IdToken token) throws FirebaseAuthException {
    try {
      if (!isSignatureValid(token)) {
        throw new FirebaseAuthException(ERROR_INVALID_CREDENTIAL,
            String.format(
                "Failed to verify the signature of Firebase %s. %s",
                shortName,
                getVerifyTokenMessage()));
      }
    } catch (GeneralSecurityException | IOException e) {
      throw new FirebaseAuthException(
          ERROR_RUNTIME_EXCEPTION, "Error while verifying signature.", e);
    }
  }

  private String getErrorIfContentInvalid(final IdToken idToken) {
    final Header header = idToken.getHeader();
    final Payload payload = idToken.getPayload();

    String errorMessage = null;
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
    } else if (!verifyTimestamps(idToken)) {
      errorMessage = String.format(
          "Firebase %s has expired or is not yet valid. Get a fresh %s and try again.",
          shortName,
          shortName);
    }

    return errorMessage;
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
  private boolean isSignatureValid(IdToken token) throws GeneralSecurityException, IOException {
    for (PublicKey key : publicKeysManager.getPublicKeys()) {
      if (token.verifySignature(key)) {
        return true;
      }
    }
    return false;
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

  private boolean verifyTimestamps(IdToken token) {
    long currentTimeMillis = idTokenVerifier.getClock().currentTimeMillis();
    return token.verifyTime(currentTimeMillis, idTokenVerifier.getAcceptableTimeSkewSeconds());
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

    FirebaseTokenVerifierImpl build() {
      return new FirebaseTokenVerifierImpl(this);
    }
  }
}
