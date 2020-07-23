/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.auth.multitenancy;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AbstractFirebaseAuth;

/**
 * The tenant-aware Firebase client.
 *
 * <p>This can be used to perform a variety of authentication-related operations, scoped to a
 * particular tenant.
 */
public final class TenantAwareFirebaseAuth extends AbstractFirebaseAuth {

  private final String tenantId;

  TenantAwareFirebaseAuth(final FirebaseApp firebaseApp, final String tenantId) {
    super(builderFromAppAndTenantId(firebaseApp, tenantId));
    checkArgument(!Strings.isNullOrEmpty(tenantId));
    this.tenantId = tenantId;
  }

  /** Returns the client's tenant ID. */
  public String getTenantId() {
    return tenantId;
  }

  @Override
  protected void doDestroy() {
    // Nothing extra needs to be destroyed.
  }
}
