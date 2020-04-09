/*
 * Copyright 2017 Google LLC
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

package com.google.firebase.auth;

import com.google.api.client.util.Clock;
import com.google.common.base.Supplier;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.auth.AbstractFirebaseAuth.Builder;
import com.google.firebase.auth.internal.FirebaseTokenFactory;
import com.google.firebase.internal.FirebaseService;

/**
 * This class is the entry point for all server-side Firebase Authentication actions.
 *
 * <p>You can get an instance of FirebaseAuth via {@link FirebaseAuth#getInstance(FirebaseApp)} and
 * then use it to perform a variety of authentication-related operations, including generating
 * custom tokens for use by client-side code, verifying Firebase ID Tokens received from clients, or
 * creating new FirebaseApp instances that are scoped to a particular authentication UID.
 */
public class FirebaseAuth extends AbstractFirebaseAuth {

  private static final String SERVICE_ID = FirebaseAuth.class.getName();

  private final Supplier<TenantManager> tenantManager;

  FirebaseAuth(final Builder builder) {
    super(builder);
    tenantManager = threadSafeMemoize(new Supplier<TenantManager>() {
      @Override
      public TenantManager get() {
        return new TenantManager(builder.firebaseApp, getUserManager());
      }
    });
  }

  public TenantManager getTenantManager() {
    return tenantManager.get();
  }

  /**
   * Gets the FirebaseAuth instance for the default {@link FirebaseApp}.
   *
   * @return The FirebaseAuth instance for the default {@link FirebaseApp}.
   */
  public static FirebaseAuth getInstance() {
    return FirebaseAuth.getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets an instance of FirebaseAuth for a specific {@link FirebaseApp}.
   *
   * @param app The {@link FirebaseApp} to get a FirebaseAuth instance for.
   * @return A FirebaseAuth instance.
   */
  public static synchronized FirebaseAuth getInstance(FirebaseApp app) {
    FirebaseAuthService service =
        ImplFirebaseTrampolines.getService(app, SERVICE_ID, FirebaseAuthService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseAuthService(app));
    }
    return service.getInstance();
  }

  private static FirebaseAuth fromApp(final FirebaseApp app) {
    return new FirebaseAuth(
        AbstractFirebaseAuth.builder()
          .setFirebaseApp(app)
          .setTokenFactory(
              new Supplier<FirebaseTokenFactory>() {
                @Override
                public FirebaseTokenFactory get() {
                  return FirebaseTokenUtils.createTokenFactory(app, Clock.SYSTEM);
                }
              })
          .setIdTokenVerifier(
              new Supplier<FirebaseTokenVerifier>() {
                @Override
                public FirebaseTokenVerifier get() {
                  return FirebaseTokenUtils.createIdTokenVerifier(app, Clock.SYSTEM);
                }
              })
          .setCookieVerifier(
              new Supplier<FirebaseTokenVerifier>() {
                @Override
                public FirebaseTokenVerifier get() {
                  return FirebaseTokenUtils.createSessionCookieVerifier(app, Clock.SYSTEM);
                }
            })
          .setUserManager(
              new Supplier<FirebaseUserManager>() {
                @Override
                public FirebaseUserManager get() {
                  return new FirebaseUserManager(app);
                }
              }));
  }

  private static class FirebaseAuthService extends FirebaseService<FirebaseAuth> {

    FirebaseAuthService(FirebaseApp app) {
      super(SERVICE_ID, FirebaseAuth.fromApp(app));
    }

    @Override
    public void destroy() {
      instance.destroy();
    }
  }
}
