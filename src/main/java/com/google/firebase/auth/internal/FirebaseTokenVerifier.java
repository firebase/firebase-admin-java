/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.util.ArrayMap;
import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseAuthException;

import com.google.firebase.internal.NonNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;

/**
 * Verifies that a JWT returned by Firebase is valid for use in the this project.
 *
 * <p>This class should be kept as a Singleton within the server in order to maximize caching of the
 * public signing keys.
 */
public final class FirebaseTokenVerifier extends IdTokenVerifier {

  static final String ID_TOKEN_CERT_URL =
      "https://www.googleapis.com/robot/v1/metadata/x509/"
          + "securetoken@system.gserviceaccount.com";
  static final String ID_TOKEN_ISSUER_PREFIX = "https://securetoken.google.com/";

  static final String SESSION_COOKIE_CERT_URL =
      "https://www.googleapis.com/identitytoolkit/v3/relyingparty/publicKeys";
  static final String SESSION_COOKIE_ISSUER_PREFIX = "https://session.firebase.google.com/";

  private static final String FIREBASE_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";

  private static final String ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL";
  private static final String ERROR_RUNTIME_EXCEPTION = "ERROR_RUNTIME_EXCEPTION";
  private static final String PROJECT_ID_MATCH_MESSAGE =
      " Make sure the %s comes from the same Firebase project as the service account used to "
          + "authenticate this SDK.";
  private static final String VERIFY_TOKEN_DOCS_MESSAGE =
      " See %s for details on how to retrieve %s.";
  private static final String ALGORITHM = "RS256";

  private final String projectId;
  private final GooglePublicKeysManager publicKeysManager;
  private final String method;
  private final String shortName;
  private final String articledShortName;

  private final String projectIdMatchMessage;
  private final String verifyTokenMessage;

  private FirebaseTokenVerifier(Builder builder) {
    super(builder);
    checkArgument(!Strings.isNullOrEmpty(builder.projectId), "projectId must be set");
    checkArgument(!Strings.isNullOrEmpty(builder.shortName), "shortName must be set");
    checkArgument(!Strings.isNullOrEmpty(builder.method), "method must be set");
    this.projectId = builder.projectId;
    this.shortName = builder.shortName;
    if ("aeiouAEIOU".indexOf(shortName.charAt(0)) < 0) {
      this.articledShortName = "a " + shortName;
    } else {
      this.articledShortName = "an " + shortName;
    }
    this.method = builder.method;
    this.publicKeysManager = checkNotNull(builder.publicKeysManager,
        "publicKeysManager must be set");
    this.projectIdMatchMessage = String.format(PROJECT_ID_MATCH_MESSAGE, shortName);
    this.verifyTokenMessage = String.format(VERIFY_TOKEN_DOCS_MESSAGE, builder.docUrl,
        articledShortName);
  }

  /**
   * We are changing the semantics of the super-class method in order to provide more details on why
   * this is failing to the developer.
   */
  public void verifyTokenAndSignature(IdToken token) throws FirebaseAuthException {
    Payload payload = token.getPayload();
    Header header = token.getHeader();
    String errorMessage = null;

    boolean isCustomToken =
        payload.getAudience() != null && payload.getAudience().equals(FIREBASE_AUDIENCE);
    boolean isLegacyCustomToken =
        header.getAlgorithm() != null
            && header.getAlgorithm().equals("HS256")
            && payload.get("v") != null
            && payload.get("v").equals(new BigDecimal(0))
            && payload.get("d") != null
            && payload.get("d") instanceof ArrayMap
            && ((ArrayMap) payload.get("d")).get("uid") != null;

    if (header.getKeyId() == null) {
      if (isCustomToken) {
        errorMessage = method + " expects " + articledShortName + ", but was given a custom token.";
      } else if (isLegacyCustomToken) {
        errorMessage = method + " expects " + articledShortName
            + ", but was given a legacy custom token.";
      } else {
        errorMessage = "Firebase " + shortName + " has no \"kid\" claim.";
      }
    } else if (header.getAlgorithm() == null || !header.getAlgorithm().equals(ALGORITHM)) {
      errorMessage =
          String.format(
              "Firebase %s has incorrect algorithm. Expected \"%s\" but got \"%s\".",
              shortName, ALGORITHM, header.getAlgorithm());
    } else if (!token.verifyAudience(getAudience())) {
      errorMessage =
          String.format(
              "Firebase %s has incorrect \"aud\" (audience) claim. Expected \"%s\" but got "
                  + "\"%s\".",
              shortName, concat(getAudience()), concat(token.getPayload().getAudienceAsList()));
      errorMessage += projectIdMatchMessage;
    } else if (!token.verifyIssuer(getIssuers())) {
      errorMessage =
          String.format(
              "Firebase %s has incorrect \"iss\" (issuer) claim. "
                  + "Expected \"%s\" but got \"%s\".",
              shortName, concat(getIssuers()), token.getPayload().getIssuer());
      errorMessage += projectIdMatchMessage;
    } else if (payload.getSubject() == null) {
      errorMessage = "Firebase " + shortName + " has no \"sub\" (subject) claim.";
    } else if (payload.getSubject().isEmpty()) {
      errorMessage = "Firebase " + shortName + " has an empty string \"sub\" (subject) claim.";
    } else if (payload.getSubject().length() > 128) {
      errorMessage = "Firebase " + shortName + " has \"sub\" (subject) claim longer than "
          + "128 characters.";
    } else if (!token.verifyTime(getClock().currentTimeMillis(), getAcceptableTimeSkewSeconds())) {
      errorMessage =
          "Firebase " + shortName + " has expired or is not yet valid. Get a fresh " + shortName
              + " and try again.";
    }

    if (errorMessage != null) {
      errorMessage += verifyTokenMessage;
      throw new FirebaseAuthException(ERROR_INVALID_CREDENTIAL, errorMessage);
    }

    try {
      if (!verifySignature(token)) {
        throw new FirebaseAuthException(
            ERROR_INVALID_CREDENTIAL, "Firebase " + shortName + " isn't signed by a valid public "
            + "key." + verifyTokenMessage);
      }
    } catch (IOException | GeneralSecurityException e) {
      throw new FirebaseAuthException(
          ERROR_RUNTIME_EXCEPTION, "Error while verifying signature.", e);
    }
  }

  private String concat(Collection<String> collection) {
    StringBuilder stringBuilder = new StringBuilder();
    for (String inputLine : collection) {
      stringBuilder.append(inputLine.trim()).append(", ");
    }
    return stringBuilder.substring(0, stringBuilder.length() - 2);
  }

  /**
   * Verifies the cryptographic signature on the FirebaseToken. Can block on a web request to fetch
   * the keys if they have expired.
   */
  private boolean verifySignature(IdToken token) throws GeneralSecurityException, IOException {
    for (PublicKey key : publicKeysManager.getPublicKeys()) {
      if (token.verifySignature(key)) {
        return true;
      }
    }
    return false;
  }

  public String getProjectId() {
    return projectId;
  }

  /** 
   * Builder for {@link FirebaseTokenVerifier}.
   */
  public static class Builder extends IdTokenVerifier.Builder {

    private String projectId;
    private String shortName;
    private String method;
    private String docUrl;
    private GooglePublicKeysManager publicKeysManager;

    public String getProjectId() {
      return projectId;
    }

    public Builder setProjectId(String issuerPrefix, String projectId) {
      this.projectId = projectId;
      this.setAudience(Collections.singleton(projectId));
      this.setIssuer(issuerPrefix + projectId);
      return this;
    }

    public Builder setShortName(String shortName) {
      this.shortName = shortName;
      return this;
    }

    public Builder setMethod(String method) {
      this.method = method;
      return this;
    }

    public Builder setDocUrl(String docUrl) {
      this.docUrl = docUrl;
      return this;
    }

    @Override
    public Builder setClock(Clock clock) {
      return (Builder) super.setClock(clock);
    }

    /** Override the GooglePublicKeysManager from the default. */
    public Builder setPublicKeysManager(GooglePublicKeysManager publicKeysManager) {
      this.publicKeysManager = publicKeysManager;
      return this;
    }

    @Override
    public FirebaseTokenVerifier build() {
      return new FirebaseTokenVerifier(this);
    }
  }

  static GooglePublicKeysManager createKeysManager(
      HttpTransport transport, Clock clock, String certUrl) {
    return new GooglePublicKeysManager.Builder(transport, new GsonFactory())
        .setClock(clock)
        .setPublicCertsEncodedUrl(certUrl)
        .build();
  }

  /**
   * Creates a new {@link FirebaseTokenVerifier} for verifying Firebase ID tokens.
   *
   * @param projectId Project ID string
   * @param keyManagers {@link KeyManagers} instance with public key managers to use
   * @param clock {@code Clock} instance for Google API client
   * @return A new {@link FirebaseTokenVerifier} instance
   */
  @NonNull public static FirebaseTokenVerifier createIdTokenVerifier(
      @NonNull String projectId, @NonNull KeyManagers keyManagers, @NonNull Clock clock) {
    return new FirebaseTokenVerifier.Builder()
        .setProjectId(ID_TOKEN_ISSUER_PREFIX, projectId)
        .setPublicKeysManager(keyManagers.getIdTokenKeysManager())
        .setShortName("ID token")
        .setMethod("verifyIdToken()")
        .setDocUrl("https://firebase.google.com/docs/auth/admin/verify-id-tokens")
        .setClock(clock)
        .build();
  }

  /**
   * Creates a new {@link FirebaseTokenVerifier} for verifying Firebase ID tokens.
   *
   * @param projectId Project ID string
   * @param keyManagers {@link KeyManagers} instance with public key managers to use
   * @param clock {@code Clock} instance for Google API client
   * @return A new {@link FirebaseTokenVerifier} instance
   */
  @NonNull public static FirebaseTokenVerifier createSessionCookieVerifier(
      @NonNull String projectId, @NonNull KeyManagers keyManagers, @NonNull Clock clock) {
    return new FirebaseTokenVerifier.Builder()
        .setProjectId(SESSION_COOKIE_ISSUER_PREFIX, projectId)
        .setPublicKeysManager(keyManagers.getSessionCookieKeysManager())
        .setShortName("session cookie")
        .setMethod("verifySessionCookie()")
        .setDocUrl("https://firebase.google.com/docs/auth/admin/manage-cookies")
        .setClock(clock)
        .build();
  }
}
