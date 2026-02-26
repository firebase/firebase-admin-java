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

package com.google.firebase.fpnv;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.fpnv.internal.FirebasePnvTokenVerifier;
import com.google.firebase.internal.FirebaseService;

/**
 * This class is the entry point for the Firebase Phone Number Verification (FPNV) service.
 *
 * <p>You can get an instance of {@link FirebasePnv} via {@link #getInstance()},
 * or {@link #getInstance(FirebaseApp)} and then use it.
 */
public final class FirebasePnv {
  private static final String SERVICE_ID = FirebasePnv.class.getName();
  private final FirebasePnvTokenVerifier tokenVerifier;

  private FirebasePnv(FirebaseApp app) {
    this.tokenVerifier = new FirebasePnvTokenVerifier(app);
  }

  /**
   * Gets the {@link FirebasePnv} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebasePnv} instance for the default {@link FirebaseApp}.
   */
  public static FirebasePnv getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebasePnv} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebasePnv} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebasePnv getInstance(FirebaseApp app) {
    FirebaseFpnvService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseFpnvService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseFpnvService(app));
    }
    return service.getInstance();
  }

  /**
   * Verifies a Firebase Phone Number Verification token (FPNV JWT).
   *
   * @param fpnvJwt The FPNV JWT string to verify.
   * @return A verified {@link FirebasePnvToken}.
   * @throws FirebasePnvException If verification fails.
   */
  public FirebasePnvToken verifyToken(String fpnvJwt) throws FirebasePnvException {
    return this.tokenVerifier.verifyToken(fpnvJwt);
  }

  private static class FirebaseFpnvService extends FirebaseService<FirebasePnv> {
    FirebaseFpnvService(FirebaseApp app) {
      super(SERVICE_ID, new FirebasePnv(app));
    }
  }
}





