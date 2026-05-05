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

package com.google.firebase.phonenumberverification;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.phonenumberverification.internal.FirebasePhoneNumberVerificationTokenVerifier;

/**
 * This class is the entry point for the Firebase Phone Number Verification service.
 *
 * <p>You can get an instance of {@link FirebasePhoneNumberVerification} via {@link #getInstance()},
 * or {@link #getInstance(FirebaseApp)} and then use it.
 */
public final class FirebasePhoneNumberVerification {
  private static final String SERVICE_ID = FirebasePhoneNumberVerification.class.getName();
  private final FirebasePhoneNumberVerificationTokenVerifier tokenVerifier;

  private FirebasePhoneNumberVerification(FirebaseApp app) {
    this.tokenVerifier = new FirebasePhoneNumberVerificationTokenVerifier(app);
  }

  /**
   * Gets the {@link FirebasePhoneNumberVerification} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebasePhoneNumberVerification} instance for the default
   *     {@link FirebaseApp}.
   */
  public static FirebasePhoneNumberVerification getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebasePhoneNumberVerification} instance for the specified
   * {@link FirebaseApp}.
   *
   * @return The {@link FirebasePhoneNumberVerification} instance for the specified
   *     {@link FirebaseApp}.
   */
  public static synchronized FirebasePhoneNumberVerification getInstance(FirebaseApp app) {
    FirebasePhoneNumberVerificationService service =
        ImplFirebaseTrampolines.getService(app, SERVICE_ID,
            FirebasePhoneNumberVerificationService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(
          app, new FirebasePhoneNumberVerificationService(app));
    }
    return service.getInstance();
  }

  /**
   * Verifies a Firebase Phone Number Verification token (JWT).
   *
   * @param phoneNumberVerificationJwt The JWT string to verify.
   * @return A verified {@link FirebasePhoneNumberVerificationToken}.
   * @throws FirebasePhoneNumberVerificationException If verification fails.
   */
  public FirebasePhoneNumberVerificationToken verifyToken(String phoneNumberVerificationJwt)
      throws FirebasePhoneNumberVerificationException {
    return this.tokenVerifier.verifyToken(phoneNumberVerificationJwt);
  }

  private static class FirebasePhoneNumberVerificationService
      extends FirebaseService<FirebasePhoneNumberVerification> {
    FirebasePhoneNumberVerificationService(FirebaseApp app) {
      super(SERVICE_ID, new FirebasePhoneNumberVerification(app));
    }
  }
}
