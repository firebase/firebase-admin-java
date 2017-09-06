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

import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdToken.Payload;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature.Header;
import com.google.api.client.util.ArrayMap;
import com.google.api.client.util.Clock;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.firebase.auth.FirebaseAuthException;

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

  @VisibleForTesting
  static final String CLIENT_CERT_URL =
      "https://www.googleapis.com/robot/v1/metadata/x509/"
          + "securetoken@system.gserviceaccount.com";
  /** The default public keys manager for verifying projects use the correct public key. */
  public static final GooglePublicKeysManager DEFAULT_KEY_MANAGER =
      new GooglePublicKeysManager.Builder(new NetHttpTransport.Builder().build(), new GsonFactory())
          .setClock(Clock.SYSTEM)
          .setPublicCertsEncodedUrl(CLIENT_CERT_URL)
          .build();

  private static final String ISSUER_PREFIX = "https://securetoken.google.com/";
  private static final String FIREBASE_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";
  private static final String ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL";
  private static final String ERROR_RUNTIME_EXCEPTION = "ERROR_RUNTIME_EXCEPTION";
  private static final String PROJECT_ID_MATCH_MESSAGE =
      " Make sure the ID token comes from the same Firebase project as the service account used to "
          + "authenticate this SDK.";
  private static final String VERIFY_ID_TOKEN_DOCS_MESSAGE =
      " See https://firebase.google.com/docs/auth/admin/verify-id-tokens for details on how to "
          + "retrieve an ID token.";
  private static final String ALGORITHM = "RS256";
  private String projectId;
  private GooglePublicKeysManager publicKeysManager;

  protected FirebaseTokenVerifier(Builder builder) {
    super(builder);
    Preconditions.checkArgument(builder.projectId != null, "projectId must be set");

    this.projectId = builder.projectId;
    this.publicKeysManager = builder.publicKeysManager;
  }

  /**
   * We are changing the semantics of the super-class method in order to provide more details on why
   * this is failing to the developer.
   */
  public boolean verifyTokenAndSignature(IdToken token) throws FirebaseAuthException {
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
        errorMessage = "verifyIdToken() expects an ID token, but was given a custom token.";
      } else if (isLegacyCustomToken) {
        errorMessage = "verifyIdToken() expects an ID token, but was given a legacy custom token.";
      } else {
        errorMessage = "Firebase ID token has no \"kid\" claim.";
      }
    } else if (header.getAlgorithm() == null || !header.getAlgorithm().equals(ALGORITHM)) {
      errorMessage =
          String.format(
              "Firebase ID token has incorrect algorithm. Expected \"%s\" but got \"%s\".",
              ALGORITHM, header.getAlgorithm());
    } else if (!token.verifyAudience(getAudience())) {
      errorMessage =
          String.format(
              "Firebase ID token has incorrect \"aud\" (audience) claim. Expected \"%s\" but got "
                  + "\"%s\".",
              concat(getAudience()), concat(token.getPayload().getAudienceAsList()));
      errorMessage += PROJECT_ID_MATCH_MESSAGE;
    } else if (!token.verifyIssuer(getIssuers())) {
      errorMessage =
          String.format(
              "Firebase ID token has incorrect \"iss\" (issuer) claim. "
                  + "Expected \"%s\" but got \"%s\".",
              concat(getIssuers()), token.getPayload().getIssuer());
      errorMessage += PROJECT_ID_MATCH_MESSAGE;
    } else if (payload.getSubject() == null) {
      errorMessage = "Firebase ID token has no \"sub\" (subject) claim.";
    } else if (payload.getSubject().isEmpty()) {
      errorMessage = "Firebase ID token has an empty string \"sub\" (subject) claim.";
    } else if (payload.getSubject().length() > 128) {
      errorMessage = "Firebase ID token has \"sub\" (subject) claim longer than 128 characters.";
    } else if (!token.verifyTime(getClock().currentTimeMillis(), getAcceptableTimeSkewSeconds())) {
      errorMessage =
          "Firebase ID token has expired or is not yet valid. Get a fresh token from your client "
              + "app and try again.";
    }

    if (errorMessage != null) {
      errorMessage += VERIFY_ID_TOKEN_DOCS_MESSAGE;
      throw new FirebaseAuthException(ERROR_INVALID_CREDENTIAL, errorMessage);
    }

    try {
      if (!verifySignature(token)) {
        throw new FirebaseAuthException(
            ERROR_INVALID_CREDENTIAL,
            "Firebase ID token isn't signed by a valid public key." + VERIFY_ID_TOKEN_DOCS_MESSAGE);
      }
    } catch (IOException | GeneralSecurityException e) {
      throw new FirebaseAuthException(
          ERROR_RUNTIME_EXCEPTION, "Error while verifying token signature.", e);
    }

    return true;
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
   *
   * <p>TODO: Wrap these blocking steps in a Task.
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

    String projectId;

    GooglePublicKeysManager publicKeysManager = DEFAULT_KEY_MANAGER;

    public String getProjectId() {
      return projectId;
    }

    public Builder setProjectId(String projectId) {
      this.projectId = projectId;

      this.setIssuer(ISSUER_PREFIX + projectId);
      this.setAudience(Collections.singleton(projectId));

      return this;
    }

    @Override
    public Builder setClock(Clock clock) {
      return (Builder) super.setClock(clock);
    }

    public GooglePublicKeysManager getPublicKeyManager() {
      return publicKeysManager;
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
}
