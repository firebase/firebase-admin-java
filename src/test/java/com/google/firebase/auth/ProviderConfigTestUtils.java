/*
 * Copyright 2020 Google LLC
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.rules.ExternalResource;

class ProviderConfigTestUtils {

  static void assertOidcProviderConfigDoesNotExist(
      AbstractFirebaseAuth firebaseAuth, String providerId) throws Exception {
    try {
      firebaseAuth.getOidcProviderConfigAsync(providerId).get();
      fail("No error thrown for getting a deleted OIDC provider config.");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      assertEquals(FirebaseUserManager.CONFIGURATION_NOT_FOUND_ERROR,
          ((FirebaseAuthException) e.getCause()).getErrorCode());
    }
  }

  /**
   * Creates temporary provider configs for testing, and deletes them at the end of each test case.
   */
  static final class TemporaryProviderConfig extends ExternalResource {

    private final AbstractFirebaseAuth auth;
    private final List<String> oidcIds = new ArrayList<>();

    TemporaryProviderConfig(AbstractFirebaseAuth auth) {
      this.auth = auth;
    }

    synchronized OidcProviderConfig createOidcProviderConfig(
        OidcProviderConfig.CreateRequest request) throws FirebaseAuthException {
      OidcProviderConfig config = auth.createOidcProviderConfig(request);
      oidcIds.add(config.getProviderId());
      return config;
    }

    synchronized void deleteOidcProviderConfig(String providerId) throws FirebaseAuthException {
      checkArgument(oidcIds.contains(providerId),
          "Provider ID is not currently associated with a temporary user.");
      auth.deleteOidcProviderConfig(providerId);
      oidcIds.remove(providerId);
    }

    @Override
    protected synchronized void after() {
      for (String id : oidcIds) {
        try {
          auth.deleteOidcProviderConfig(id);
        } catch (Exception ignore) {
          // Ignore
        }
      }
      oidcIds.clear();
    }
  }
}

