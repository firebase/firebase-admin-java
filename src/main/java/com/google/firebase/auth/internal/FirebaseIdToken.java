/*
 * Copyright 2019 Google Inc.
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
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.Key;
import java.io.IOException;

public final class FirebaseIdToken extends IdToken {

  private FirebaseIdToken(
      Header header, Payload payload, byte[] signatureBytes, byte[] signedContentBytes) {
    super(header, payload, signatureBytes, signedContentBytes);
  }

  @Override
  public Payload getPayload() {
    return (Payload) super.getPayload();
  }

  static FirebaseIdToken fromString(String jwt, JsonFactory jsonFactory) throws IOException {
    JsonWebSignature jws = JsonWebSignature.parser(jsonFactory)
        .setPayloadClass(FirebaseIdToken.Payload.class)
        .parse(jwt);
    return new FirebaseIdToken(
        jws.getHeader(),
        (FirebaseIdToken.Payload) jws.getPayload(),
        jws.getSignatureBytes(),
        jws.getSignedContentBytes());
  }

  /** Represents a FirebaseWebToken Payload. */
  public static final class Payload extends IdToken.Payload {

    /**
     * Timestamp of the last time this user authenticated with Firebase on the device receiving
     * this token.
     */
    @Key("auth_time")
    private long authTime;

    /** User's primary email address. */
    @Key private String email;

    /** Indicates whether or not the e-mail field is verified to be a known-good address. */
    @Key("email_verified")
    private boolean emailVerified;

    /** User's Display Name. */
    @Key private String name;

    /** URI of the User's profile picture. */
    @Key private String picture;

    /**
     * Returns the UID of the user represented by this token. This is an alias for {@link
     * #getSubject()}
     */
    public String getUid() {
      return getSubject();
    }

    /**
     * Returns the time in seconds from the Unix Epoch that this user last authenticated with
     * Firebase on this device.
     */
    public long getAuthTime() {
      return authTime;
    }

    /**
     * Returns the e-mail address for this user, or {@code null} if it's unavailable.
     */
    public String getEmail() {
      return email;
    }

    /**
     * Indicates if the email address returned by {@link #getEmail()} has been verified as good.
     */
    public boolean isEmailVerified() {
      return emailVerified;
    }

    /** Returns the user's display name. */
    public String getName() {
      return name;
    }

    /** Returns the Uri string of the user's profile photo. */
    public String getPicture() {
      return picture;
    }
  }
}
