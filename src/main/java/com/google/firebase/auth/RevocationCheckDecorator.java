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

import com.google.common.base.Strings;
import com.google.firebase.auth.internal.FirebaseIdToken;
import com.google.firebase.auth.internal.FirebaseTokenVerifier;

class RevocationCheckDecorator implements FirebaseTokenVerifier {

  static final String ID_TOKEN_REVOKED_ERROR = "id-token-revoked";
  static final String SESSION_COOKIE_REVOKED_ERROR = "session-cookie-revoked";

  private final FirebaseTokenVerifier tokenVerifier;
  private final FirebaseUserManager userManager;
  private final String errorCode;
  private final String shortName;

  private RevocationCheckDecorator(
      FirebaseTokenVerifier tokenVerifier,
      FirebaseUserManager userManager,
      String errorCode,
      String shortName) {
    this.tokenVerifier = checkNotNull(tokenVerifier);
    this.userManager = checkNotNull(userManager);
    checkArgument(!Strings.isNullOrEmpty(errorCode));
    checkArgument(!Strings.isNullOrEmpty(shortName));
    this.errorCode = errorCode;
    this.shortName = shortName;
  }

  @Override
  public FirebaseIdToken verifyToken(String token) throws FirebaseAuthException {
    FirebaseIdToken firebaseToken = tokenVerifier.verifyToken(token);
    if (isRevoked(firebaseToken.getPayload())) {
      throw new FirebaseAuthException(errorCode, "Firebase " + shortName + " revoked");
    }
    return firebaseToken;
  }

  private boolean isRevoked(FirebaseIdToken.Payload tokenPayload) throws FirebaseAuthException {
    String uid = tokenPayload.getUid();
    UserRecord user = userManager.getUserById(uid);
    long issuedAtInSeconds = tokenPayload.getIssuedAtTimeSeconds();
    return user.getTokensValidAfterTimestamp() > issuedAtInSeconds * 1000;
  }

  static FirebaseTokenVerifier decorateIdTokenVerifier(
      FirebaseTokenVerifier tokenVerifier, FirebaseUserManager userManager) {
    return new RevocationCheckDecorator(
        tokenVerifier, userManager, ID_TOKEN_REVOKED_ERROR, "id token");
  }

  static FirebaseTokenVerifier decorateSessionCookieVerifier(
      FirebaseTokenVerifier tokenVerifier, FirebaseUserManager userManager) {
    return new RevocationCheckDecorator(
        tokenVerifier, userManager, SESSION_COOKIE_REVOKED_ERROR, "session cookie");
  }
}
