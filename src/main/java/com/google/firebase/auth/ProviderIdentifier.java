/*
 * Copyright 2020 Google Inc.
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

import com.google.firebase.auth.internal.GetAccountInfoRequest;
import com.google.firebase.internal.NonNull;

/**
 * Used for looking up an account by provider.
 *
 * @see {FirebaseAuth#getUsers}
 */
public final class ProviderIdentifier extends UserIdentifier {
  private final String providerId;
  private final String providerUid;

  public ProviderIdentifier(@NonNull String providerId, @NonNull String providerUid) {
    UserRecord.checkProvider(providerId, providerUid);
    this.providerId = providerId;
    this.providerUid = providerUid;
  }

  @Override
  public String toString() {
    return "ProviderIdentifier(" + providerId + ", " + providerUid + ")";
  }

  @Override
  void populate(@NonNull GetAccountInfoRequest payload) {
    payload.addFederatedUserId(providerId, providerUid);
  }

  @Override
  boolean matches(@NonNull UserRecord userRecord) {
    for (UserInfo userInfo : userRecord.getProviderData()) {
      if (providerId.equals(userInfo.getProviderId()) && providerUid.equals(userInfo.getUid())) {
        return true;
      }
    }
    return false;
  }
}
