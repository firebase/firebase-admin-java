/*
 * Copyright 2019 Google LLC
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
import com.google.firebase.ErrorCode;

/**
 * A decorator for adding token revocation checks to an existing {@link FirebaseTokenVerifier}.
 */
class RevocationCheckDecorator implements FirebaseTokenVerifier {

  private final FirebaseTokenVerifier tokenVerifier;
  private final FirebaseUserManager userManager;
  private final AuthErrorCode errorCode;
  private final String shortName;

  private RevocationCheckDecorator(
      FirebaseTokenVerifier tokenVerifier,
      FirebaseUserManager userManager,
      AuthErrorCode errorCode,
      String shortName) {
    this.tokenVerifier = checkNotNull(tokenVerifier);
    this.userManager = checkNotNull(userManager);
    this.errorCode = checkNotNull(errorCode);
    checkArgument(!Strings.isNullOrEmpty(shortName));
    this.shortName = shortName;
  }

  /**
   * If the wrapped {@link FirebaseTokenVerifier} deems the input token string is valid, checks
   * whether the token has been revoked.
   */
  @Override
  public FirebaseToken verifyToken(String token) throws FirebaseAuthException {
    FirebaseToken firebaseToken = tokenVerifier.verifyToken(token);
    validateDisabledOrRevoked(firebaseToken);
    return firebaseToken;
  }

  private void validateDisabledOrRevoked(FirebaseToken firebaseToken) throws FirebaseAuthException {
    UserRecord user = userManager.getUserById(firebaseToken.getUid());
    if (user.isDisabled()) {
      throw new FirebaseAuthException(ErrorCode.INVALID_ARGUMENT,
          "The user record is disabled.",
          /* cause= */ null,
          /* response= */ null,
          AuthErrorCode.USER_DISABLED);
    }
    long issuedAtInSeconds = (long) firebaseToken.getClaims().get("iat");
    if (user.getTokensValidAfterTimestamp() > issuedAtInSeconds * 1000) {
      throw new FirebaseAuthException(
          ErrorCode.INVALID_ARGUMENT,
          "Firebase " + shortName + " is revoked.",
          null,
          null,
          errorCode);
    }
  }

  static RevocationCheckDecorator decorateIdTokenVerifier(
      FirebaseTokenVerifier tokenVerifier, FirebaseUserManager userManager) {
    return new RevocationCheckDecorator(
        tokenVerifier, userManager, AuthErrorCode.REVOKED_ID_TOKEN, "id token");
  }

  static RevocationCheckDecorator decorateSessionCookieVerifier(
      FirebaseTokenVerifier tokenVerifier, FirebaseUserManager userManager) {
    return new RevocationCheckDecorator(
        tokenVerifier, userManager, AuthErrorCode.REVOKED_SESSION_COOKIE, "session cookie");
  }
}
