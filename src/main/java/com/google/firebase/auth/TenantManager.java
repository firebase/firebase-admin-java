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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.JsonFactory;
import com.google.api.core.ApiFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.ListTenantsPage.DefaultTenantSource;
import com.google.firebase.auth.ListTenantsPage.PageFactory;
import com.google.firebase.auth.ListTenantsPage.TenantSource;
import com.google.firebase.auth.Tenant.CreateRequest;
import com.google.firebase.auth.Tenant.UpdateRequest;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

/**
 * This class can be used to perform a variety of tenant-related operations, including creating,
 * updating, and listing tenants.
 *
 * <p>TODO(micahstairs): Implement the following methods: getAuthForTenant(), getTenant(),
 * deleteTenant(), createTenant(), and updateTenant().
 */
public class TenantManager {

  private final FirebaseApp firebaseApp;
  private final FirebaseUserManager userManager;

  public TenantManager(FirebaseApp firebaseApp, FirebaseUserManager userManager) {
    this.firebaseApp = firebaseApp;
    this.userManager = userManager;
  }

  /**
   * Gets a page of tenants starting from the specified {@code pageToken}. Page size will be limited
   * to 1000 tenants.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of tenants.
   * @return A {@link ListTenantsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty.
   * @throws FirebaseAuthException If an error occurs while retrieving tenant data.
   */
  public ListTenantsPage listTenants(@Nullable String pageToken) throws FirebaseAuthException {
    return listTenants(pageToken, FirebaseUserManager.MAX_LIST_TENANTS_RESULTS);
  }

  /**
   * Gets a page of tenants starting from the specified {@code pageToken}.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of tenants.
   * @param maxResults Maximum number of tenants to include in the returned page. This may not
   *     exceed 1000.
   * @return A {@link ListTenantsPage} instance.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   * @throws FirebaseAuthException If an error occurs while retrieving tenant data.
   */
  public ListTenantsPage listTenants(@Nullable String pageToken, int maxResults)
      throws FirebaseAuthException {
    return listTenantsOp(pageToken, maxResults).call();
  }

  /**
   * Similar to {@link #listTenants(String)} but performs the operation asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of tenants.
   * @return An {@code ApiFuture} which will complete successfully with a {@link ListTenantsPage}
   *     instance. If an error occurs while retrieving tenant data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty.
   */
  public ApiFuture<ListTenantsPage> listTenantsAsync(@Nullable String pageToken) {
    return listTenantsAsync(pageToken, FirebaseUserManager.MAX_LIST_TENANTS_RESULTS);
  }

  /**
   * Similar to {@link #listTenants(String, int)} but performs the operation asynchronously.
   *
   * @param pageToken A non-empty page token string, or null to retrieve the first page of tenants.
   * @param maxResults Maximum number of tenants to include in the returned page. This may not
   *     exceed 1000.
   * @return An {@code ApiFuture} which will complete successfully with a {@link ListTenantsPage}
   *     instance. If an error occurs while retrieving tenant data, the future throws an exception.
   * @throws IllegalArgumentException If the specified page token is empty, or max results value is
   *     invalid.
   */
  public ApiFuture<ListTenantsPage> listTenantsAsync(@Nullable String pageToken, int maxResults) {
    return listTenantsOp(pageToken, maxResults).callAsync(firebaseApp);
  }

  private CallableOperation<ListTenantsPage, FirebaseAuthException> listTenantsOp(
      @Nullable final String pageToken, final int maxResults) {
    // TODO(micahstairs): Add a check to make sure the app has not been destroyed yet.
    final TenantSource tenantSource = new DefaultTenantSource(userManager);
    final PageFactory factory = new PageFactory(tenantSource, maxResults, pageToken);
    return new CallableOperation<ListTenantsPage, FirebaseAuthException>() {
      @Override
      protected ListTenantsPage execute() throws FirebaseAuthException {
        return factory.create();
      }
    };
  }
}
