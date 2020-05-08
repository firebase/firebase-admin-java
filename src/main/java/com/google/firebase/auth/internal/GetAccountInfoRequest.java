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

package com.google.firebase.auth.internal;

import com.google.api.client.util.Key;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the request to look up account information.
 */
public final class GetAccountInfoRequest {

  @Key("localId")
  private List<String> uids = null;

  @Key("email")
  private List<String> emails = null;

  @Key("phoneNumber")
  private List<String> phoneNumbers = null;

  @Key("federatedUserId")
  private List<FederatedUserId> federatedUserIds = null;

  private static final class FederatedUserId {
    @Key("providerId")
    private String providerId = null;

    @Key("rawId")
    private String rawId = null;

    FederatedUserId(String providerId, String rawId) {
      this.providerId = providerId;
      this.rawId = rawId;
    }
  }

  public void addUid(String uid) {
    if (uids == null) {
      uids = new ArrayList<>();
    }
    uids.add(uid);
  }

  public void addEmail(String email) {
    if (emails == null) {
      emails = new ArrayList<>();
    }
    emails.add(email);
  }

  public void addPhoneNumber(String phoneNumber) {
    if (phoneNumbers == null) {
      phoneNumbers = new ArrayList<>();
    }
    phoneNumbers.add(phoneNumber);
  }

  public void addFederatedUserId(String providerId, String providerUid) {
    if (federatedUserIds == null) {
      federatedUserIds = new ArrayList<>();
    }
    federatedUserIds.add(new FederatedUserId(providerId, providerUid));
  }
}
