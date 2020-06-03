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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.api.client.json.JsonFactory;
import com.google.api.core.ApiFuture;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.ListTenantsPage.DefaultTenantSource;
import com.google.firebase.auth.ListTenantsPage.PageFactory;
import com.google.firebase.auth.ListTenantsPage.TenantSource;
import com.google.firebase.auth.Tenant.CreateRequest;
import com.google.firebase.auth.Tenant.UpdateRequest;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class can be used to perform a variety of tenant-related operations, including creating,
 * updating, and listing tenants.
 */
public final class TenantManager {

  private final Object lock = new Object();
  private final AtomicBoolean destroyed = new AtomicBoolean(false);

  private final FirebaseApp firebaseApp;
  private final FirebaseUserManager userManager;
  private final Map<String, TenantAwareFirebaseAuth> tenantAwareAuths;

  TenantManager(FirebaseApp firebaseApp, FirebaseUserManager userManager) {
    this.firebaseApp = firebaseApp;
    this.userManager = userManager;
    tenantAwareAuths = new HashMap<String, TenantAwareFirebaseAuth>();
  }

  /**
   * Gets the tenant corresponding to the specified tenant ID.
   *
   * @param tenantId A tenant ID string.
   * @return A {@link Tenant} instance.
   * @throws IllegalArgumentException If the tenant ID string is null or empty.
   * @throws FirebaseAuthException If an error occurs while retrieving user data.
   */
  public Tenant getTenant(@NonNull String tenantId) throws FirebaseAuthException {
    return getTenantOp(tenantId).call();
  }

  public synchronized TenantAwareFirebaseAuth getAuthForTenant(@NonNull String tenantId) {
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    if (!tenantAwareAuths.containsKey(tenantId)) {
      tenantAwareAuths.put(tenantId, new TenantAwareFirebaseAuth(firebaseApp, tenantId));
    }
    return tenantAwareAuths.get(tenantId);
  }

  /**
   * Similar to {@link #getTenant(String)} but performs the operation asynchronously.
   *
   * @param tenantId A tenantId string.
   * @return An {@code ApiFuture} which will complete successfully with a {@link Tenant} instance
   *     If an error occurs while retrieving tenant data or if the specified tenant ID does not
   *     exist, the future throws a {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the tenant ID string is null or empty.
   */
  public ApiFuture<Tenant> getTenantAsync(@NonNull String tenantId) {
    return getTenantOp(tenantId).callAsync(firebaseApp);
  }

  private CallableOperation<Tenant, FirebaseAuthException> getTenantOp(final String tenantId) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    return new CallableOperation<Tenant, FirebaseAuthException>() {
      @Override
      protected Tenant execute() throws FirebaseAuthException {
        return userManager.getTenant(tenantId);
      }
    };
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
    checkNotDestroyed();
    final TenantSource tenantSource = new DefaultTenantSource(userManager);
    final PageFactory factory = new PageFactory(tenantSource, maxResults, pageToken);
    return new CallableOperation<ListTenantsPage, FirebaseAuthException>() {
      @Override
      protected ListTenantsPage execute() throws FirebaseAuthException {
        return factory.create();
      }
    };
  }

  /**
   * Creates a new tenant with the attributes contained in the specified {@link CreateRequest}.
   *
   * @param request A non-null {@link CreateRequest} instance.
   * @return A {@link Tenant} instance corresponding to the newly created tenant.
   * @throws NullPointerException if the provided request is null.
   * @throws FirebaseAuthException if an error occurs while creating the tenant.
   */
  public Tenant createTenant(@NonNull CreateRequest request) throws FirebaseAuthException {
    return createTenantOp(request).call();
  }

  /**
   * Similar to {@link #createTenant(CreateRequest)} but performs the operation asynchronously.
   *
   * @param request A non-null {@link CreateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link Tenant}
   *     instance corresponding to the newly created tenant. If an error occurs while creating the
   *     tenant, the future throws a {@link FirebaseAuthException}.
   * @throws NullPointerException if the provided request is null.
   */
  public ApiFuture<Tenant> createTenantAsync(@NonNull CreateRequest request) {
    return createTenantOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<Tenant, FirebaseAuthException> createTenantOp(
      final CreateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "Create request must not be null.");
    return new CallableOperation<Tenant, FirebaseAuthException>() {
      @Override
      protected Tenant execute() throws FirebaseAuthException {
        return userManager.createTenant(request);
      }
    };
  }


  /**
   * Updates an existing user account with the attributes contained in the specified {@link
   * UpdateRequest}.
   *
   * @param request A non-null {@link UpdateRequest} instance.
   * @return A {@link Tenant} instance corresponding to the updated user account.
   * @throws NullPointerException if the provided update request is null.
   * @throws FirebaseAuthException if an error occurs while updating the user account.
   */
  public Tenant updateTenant(@NonNull UpdateRequest request) throws FirebaseAuthException {
    return updateTenantOp(request).call();
  }

  /**
   * Similar to {@link #updateTenant(UpdateRequest)} but performs the operation asynchronously.
   *
   * @param request A non-null {@link UpdateRequest} instance.
   * @return An {@code ApiFuture} which will complete successfully with a {@link Tenant}
   *     instance corresponding to the updated user account. If an error occurs while updating the
   *     user account, the future throws a {@link FirebaseAuthException}.
   */
  public ApiFuture<Tenant> updateTenantAsync(@NonNull UpdateRequest request) {
    return updateTenantOp(request).callAsync(firebaseApp);
  }

  private CallableOperation<Tenant, FirebaseAuthException> updateTenantOp(
      final UpdateRequest request) {
    checkNotDestroyed();
    checkNotNull(request, "Update request must not be null.");
    checkArgument(!request.getProperties().isEmpty(),
        "Tenant update must have at least one property set.");
    return new CallableOperation<Tenant, FirebaseAuthException>() {
      @Override
      protected Tenant execute() throws FirebaseAuthException {
        return userManager.updateTenant(request);
      }
    };
  }

  /**
   * Deletes the tenant identified by the specified tenant ID.
   *
   * @param tenantId A tenant ID string.
   * @throws IllegalArgumentException If the tenant ID string is null or empty.
   * @throws FirebaseAuthException If an error occurs while deleting the tenant.
   */
  public void deleteTenant(@NonNull String tenantId) throws FirebaseAuthException {
    deleteTenantOp(tenantId).call();
  }

  /**
   * Similar to {@link #deleteTenant(String)} but performs the operation asynchronously.
   *
   * @param tenantId A tenant ID string.
   * @return An {@code ApiFuture} which will complete successfully when the specified tenant account
   *     has been deleted. If an error occurs while deleting the tenant account, the future throws a
   *     {@link FirebaseAuthException}.
   * @throws IllegalArgumentException If the tenant ID string is null or empty.
   */
  public ApiFuture<Void> deleteTenantAsync(String tenantId) {
    return deleteTenantOp(tenantId).callAsync(firebaseApp);
  }

  private CallableOperation<Void, FirebaseAuthException> deleteTenantOp(final String tenantId) {
    checkNotDestroyed();
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    return new CallableOperation<Void, FirebaseAuthException>() {
      @Override
      protected Void execute() throws FirebaseAuthException {
        userManager.deleteTenant(tenantId);
        return null;
      }
    };
  }

  void checkNotDestroyed() {
    synchronized (lock) {
      checkState(
          !destroyed.get(),
          "TenantManager instance is no longer alive. This happens when "
              + "the parent FirebaseApp instance has been deleted.");
    }
  }

  protected void destroy() {
    destroyed.set(true);
  }
}
