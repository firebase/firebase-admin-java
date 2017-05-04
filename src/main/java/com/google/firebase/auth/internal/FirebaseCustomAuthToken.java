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
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Key;
import com.google.firebase.auth.FirebaseToken;

import java.io.IOException;

/**
 * Implementation of a JWT used for Firebase Custom Auth.
 *
 * <p>These JWTs are minted by the developer's application and signed by the developer's Private Key
 * and used to trigger an authentication event. These will be exchanged with SecureTokenService for
 * a {@link FirebaseToken}, which is what will actually be sent to Google to perform actions against
 * the Firebase APIs on behalf of the user created, or signed in, using a FirebaseCustomAuthToken.
 */
public final class FirebaseCustomAuthToken extends IdToken {

  static final String FIREBASE_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";
  static final long TOKEN_DURATION_SECONDS = 3600L; // 1 hour

  public FirebaseCustomAuthToken(
      Header header, Payload payload, byte[] signatureBytes, byte[] signedContentBytes) {
    super(header, payload, signatureBytes, signedContentBytes);
  }

  /** Parses a JWT token string and extracts its headers and payload fields. */
  public static FirebaseCustomAuthToken parse(JsonFactory jsonFactory, String tokenString)
      throws IOException {
    JsonWebSignature jws =
        JsonWebSignature.parser(jsonFactory).setPayloadClass(Payload.class).parse(tokenString);
    return new FirebaseCustomAuthToken(
        jws.getHeader(),
        (Payload) jws.getPayload(),
        jws.getSignatureBytes(),
        jws.getSignedContentBytes());
  }

  @Override
  public Payload getPayload() {
    return (Payload) super.getPayload();
  }

  /** Represents a FirebaseCustomAuthToken Payload. */
  public static class Payload extends IdToken.Payload {

    /** The uid of the user to store in the Firebase data store. */
    @Key("uid")
    private String uid;

    /**
     * Any additional claims the developer wishes stored and signed by Firebase.
     *
     * <p>TODO: Come up with a solution to allow this to be parsed as the
     * correct type.
     */
    @Key("claims")
    private GenericJson developerClaims;

    public final String getUid() {
      return uid;
    }

    public Payload setUid(String uid) {
      this.uid = uid;
      return this;
    }

    public final GenericJson getDeveloperClaims() {
      return developerClaims;
    }

    public Payload setDeveloperClaims(GenericJson developerClaims) {
      this.developerClaims = developerClaims;
      return this;
    }

    @Override
    public Payload setIssuer(String issuer) {
      return (Payload) super.setIssuer(issuer);
    }

    @Override
    public Payload setSubject(String subject) {
      return (Payload) super.setSubject(subject);
    }

    @Override
    public Payload setAudience(Object audience) {
      return (Payload) super.setAudience(audience);
    }

    @Override
    public Payload setIssuedAtTimeSeconds(Long issuedAt) {
      return (Payload) super.setIssuedAtTimeSeconds(issuedAt);
    }

    @Override
    public Payload setExpirationTimeSeconds(Long expirationTime) {
      return (Payload) super.setExpirationTimeSeconds(expirationTime);
    }
  }
}
