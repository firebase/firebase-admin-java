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

import com.google.api.core.ApiAsyncFunction;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AbstractFirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.SessionCookieOptions;

/**
 * The tenant-aware Firebase client.
 *
 * <p>This can be used to perform a variety of authentication-related operations, scoped to a
 * particular tenant.
 */
public final class TenantAwareFirebaseAuth extends AbstractFirebaseAuth {

  private final String tenantId;

  private TenantAwareFirebaseAuth(Builder builder) {
    super(builder);
    checkArgument(!Strings.isNullOrEmpty(builder.tenantId));
    this.tenantId = builder.tenantId;
  }

  /** Returns the client's tenant ID. */
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String createSessionCookie(
      String idToken, SessionCookieOptions options) throws FirebaseAuthException {
    verifyIdToken(idToken);
    return super.createSessionCookie(idToken, options);
  }

  @Override
  public ApiFuture<String> createSessionCookieAsync(
      final String idToken, final SessionCookieOptions options) {
    ApiFuture<FirebaseToken> future = verifyIdTokenAsync(idToken);
    return ApiFutures.transformAsync(future, new ApiAsyncFunction<FirebaseToken, String>() {
      @Override
      public ApiFuture<String> apply(FirebaseToken input) {
        return TenantAwareFirebaseAuth.super.createSessionCookieAsync(idToken, options);
      }
    }, MoreExecutors.directExecutor());
  }

  static TenantAwareFirebaseAuth fromApp(FirebaseApp app, String tenantId) {
    return populateBuilderFromApp(builder(), app, tenantId)
        .setTenantId(tenantId)
        .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder extends AbstractFirebaseAuth.Builder<Builder> {

    private String tenantId;

    private Builder() { }

    @Override
    protected Builder getThis() {
      return this;
    }

    public Builder setTenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    TenantAwareFirebaseAuth build() {
      return new TenantAwareFirebaseAuth(this);
    }
  }
}
