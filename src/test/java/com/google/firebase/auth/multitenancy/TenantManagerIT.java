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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class TenantManagerIT {

  private static final FirebaseAuth auth = FirebaseAuth.getInstance(
      IntegrationTestUtils.ensureDefaultApp());

  @Test
  public void testTenantLifecycle() throws Exception {
    TenantManager tenantManager = auth.getTenantManager();

    // Create tenant
    Tenant.CreateRequest createRequest = new Tenant.CreateRequest().setDisplayName("DisplayName");
    Tenant tenant = tenantManager.createTenantAsync(createRequest).get();
    assertEquals("DisplayName", tenant.getDisplayName());
    assertFalse(tenant.isPasswordSignInAllowed());
    assertFalse(tenant.isEmailLinkSignInEnabled());
    String tenantId = tenant.getTenantId();

    // Get tenant
    tenant = tenantManager.getTenantAsync(tenantId).get();
    assertEquals(tenantId, tenant.getTenantId());
    assertEquals("DisplayName", tenant.getDisplayName());
    assertFalse(tenant.isPasswordSignInAllowed());
    assertFalse(tenant.isEmailLinkSignInEnabled());

    // Update tenant
    Tenant.UpdateRequest updateRequest = tenant.updateRequest()
        .setDisplayName("UpdatedName")
        .setPasswordSignInAllowed(true)
        .setEmailLinkSignInEnabled(true);
    tenant = tenantManager.updateTenantAsync(updateRequest).get();
    assertEquals(tenantId, tenant.getTenantId());
    assertEquals("UpdatedName", tenant.getDisplayName());
    assertTrue(tenant.isPasswordSignInAllowed());
    assertTrue(tenant.isEmailLinkSignInEnabled());

    // Delete tenant
    tenantManager.deleteTenantAsync(tenant.getTenantId()).get();
    try {
      tenantManager.getTenantAsync(tenant.getTenantId()).get();
      fail("No error thrown for getting a deleted tenant");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseAuthException);
      FirebaseAuthException authException = (FirebaseAuthException) e.getCause();
      assertEquals(ErrorCode.NOT_FOUND, authException.getErrorCode());
      assertEquals(AuthErrorCode.TENANT_NOT_FOUND, authException.getAuthErrorCode());
    }
  }

  @Test
  public void testListTenants() throws Exception {
    TenantManager tenantManager = auth.getTenantManager();
    final List<String> tenantIds = new ArrayList<>();

    try {
      for (int i = 0; i < 3; i++) {
        Tenant.CreateRequest createRequest =
            new Tenant.CreateRequest().setDisplayName("DisplayName" + i);
        tenantIds.add(tenantManager.createTenantAsync(createRequest).get().getTenantId());
      }

      // Test list by batches
      final AtomicInteger collected = new AtomicInteger(0);
      ListTenantsPage page = tenantManager.listTenantsAsync(null).get();
      while (page != null) {
        for (Tenant tenant : page.getValues()) {
          if (tenantIds.contains(tenant.getTenantId())) {
            collected.incrementAndGet();
            assertNotNull(tenant.getDisplayName());
          }
        }
        page = page.getNextPage();
      }
      assertEquals(tenantIds.size(), collected.get());

      // Test iterate all
      collected.set(0);
      page = tenantManager.listTenantsAsync(null).get();
      for (Tenant tenant : page.iterateAll()) {
        if (tenantIds.contains(tenant.getTenantId())) {
          collected.incrementAndGet();
          assertNotNull(tenant.getDisplayName());
        }
      }
      assertEquals(tenantIds.size(), collected.get());

      // Test iterate async
      collected.set(0);
      final Semaphore semaphore = new Semaphore(0);
      final AtomicReference<Throwable> error = new AtomicReference<>();
      ApiFuture<ListTenantsPage> pageFuture = tenantManager.listTenantsAsync(null);
      ApiFutures.addCallback(pageFuture, new ApiFutureCallback<ListTenantsPage>() {
        @Override
        public void onFailure(Throwable t) {
          error.set(t);
          semaphore.release();
        }

        @Override
        public void onSuccess(ListTenantsPage result) {
          for (Tenant tenant : result.iterateAll()) {
            if (tenantIds.contains(tenant.getTenantId())) {
              collected.incrementAndGet();
              assertNotNull(tenant.getDisplayName());
            }
          }
          semaphore.release();
        }
      }, MoreExecutors.directExecutor());
      semaphore.acquire();
      assertEquals(tenantIds.size(), collected.get());
      assertNull(error.get());
    } finally {
      for (String tenantId : tenantIds) {
        tenantManager.deleteTenantAsync(tenantId).get();
      }
    }
  }
}
