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
 * Used for looking up an account by uid.
 *
 * @see {FirebaseAuth#getUsers}
 */
public final class UidIdentifier extends UserIdentifier {
  private final String uid;

  public UidIdentifier(@NonNull String uid) {
    UserRecord.checkUid(uid);
    this.uid = uid;
  }

  @Override
  public String toString() {
    return "UidIdentifier(" + uid + ")";
  }

  @Override
  void populate(@NonNull GetAccountInfoRequest payload) {
    payload.addUid(uid);
  }

  @Override
  boolean matches(@NonNull UserRecord userRecord) {
    return uid.equals(userRecord.getUid());
  }
}
