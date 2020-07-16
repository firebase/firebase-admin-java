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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.internal.ListTenantsResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

public class ListTenantsPageTest {

  @Test
  public void testSinglePage() throws FirebaseAuthException, IOException {
    TestTenantSource source = new TestTenantSource(3);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListTenantsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());

    ImmutableList<Tenant> tenants = ImmutableList.copyOf(page.getValues());
    assertEquals(3, tenants.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("tenant" + i, tenants.get(i).getTenantId());
    }
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testMultiplePages() throws FirebaseAuthException, IOException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.of(newTenant("tenant0"), newTenant("tenant1"), newTenant("tenant2")),
        "token");
    TestTenantSource source = new TestTenantSource(response);
    ListTenantsPage page1 = new ListTenantsPage.PageFactory(source).create();
    assertTrue(page1.hasNextPage());
    assertEquals("token", page1.getNextPageToken());
    ImmutableList<Tenant> tenants = ImmutableList.copyOf(page1.getValues());
    assertEquals(3, tenants.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("tenant" + i, tenants.get(i).getTenantId());
    }

    response = new ListTenantsResponse(
        ImmutableList.of(newTenant("tenant3"), newTenant("tenant4"), newTenant("tenant5")),
        ListTenantsPage.END_OF_LIST);
    source.response = response;
    ListTenantsPage page2 = page1.getNextPage();
    assertFalse(page2.hasNextPage());
    assertEquals(ListTenantsPage.END_OF_LIST, page2.getNextPageToken());
    tenants = ImmutableList.copyOf(page2.getValues());
    assertEquals(3, tenants.size());
    for (int i = 3; i < 6; i++) {
      assertEquals("tenant" + i, tenants.get(i - 3).getTenantId());
    }

    assertEquals(2, source.calls.size());
    assertNull(source.calls.get(0));
    assertEquals("token", source.calls.get(1));

    // Should iterate all tenants from both pages
    int iterations = 0;
    for (Tenant tenant : page1.iterateAll()) {
      iterations++;
    }
    assertEquals(6, iterations);
    assertEquals(3, source.calls.size());
    assertEquals("token", source.calls.get(2));

    // Should only iterate tenants in the last page
    iterations = 0;
    for (Tenant tenant : page2.iterateAll()) {
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(3, source.calls.size());
  }

  @Test
  public void testListTenantsIterable() throws FirebaseAuthException, IOException {
    TestTenantSource source = new TestTenantSource(3);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    Iterable<Tenant> tenants = page.iterateAll();

    int iterations = 0;
    for (Tenant tenant : tenants) {
      assertEquals("tenant" + iterations, tenant.getTenantId());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    // Should result in a new iterator
    iterations = 0;
    for (Tenant tenant : tenants) {
      assertEquals("tenant" + iterations, tenant.getTenantId());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testListTenantsIterator() throws FirebaseAuthException, IOException {
    TestTenantSource source = new TestTenantSource(3);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    Iterable<Tenant> tenants = page.iterateAll();
    Iterator<Tenant> iterator = tenants.iterator();
    int iterations = 0;
    while (iterator.hasNext()) {
      assertEquals("tenant" + iterations, iterator.next().getTenantId());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    while (iterator.hasNext()) {
      fail("Should not be able to to iterate any more");
    }
    try {
      iterator.next();
      fail("Should not be able to iterate any more");
    } catch (NoSuchElementException expected) {
      // expected
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testListTenantsPagedIterable() throws FirebaseAuthException, IOException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.of(newTenant("tenant0"), newTenant("tenant1"), newTenant("tenant2")),
        "token");
    TestTenantSource source = new TestTenantSource(response);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    int iterations = 0;
    for (Tenant tenant : page.iterateAll()) {
      assertEquals("tenant" + iterations, tenant.getTenantId());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        response = new ListTenantsResponse(
            ImmutableList.of(newTenant("tenant3"), newTenant("tenant4"), newTenant("tenant5")),
            ListTenantsPage.END_OF_LIST);
        source.response = response;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
  }

  @Test
  public void testListTenantsPagedIterator() throws FirebaseAuthException, IOException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.of(newTenant("tenant0"), newTenant("tenant1"), newTenant("tenant2")),
        "token");
    TestTenantSource source = new TestTenantSource(response);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    Iterator<Tenant> tenants = page.iterateAll().iterator();
    int iterations = 0;
    while (tenants.hasNext()) {
      assertEquals("tenant" + iterations, tenants.next().getTenantId());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        response = new ListTenantsResponse(
            ImmutableList.of(newTenant("tenant3"), newTenant("tenant4"), newTenant("tenant5")),
            ListTenantsPage.END_OF_LIST);
        source.response = response;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
    assertFalse(tenants.hasNext());
    try {
      tenants.next();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void testPageWithNoTenants() throws FirebaseAuthException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.<Tenant>of(),
        ListTenantsPage.END_OF_LIST);
    TestTenantSource source = new TestTenantSource(response);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListTenantsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());
    assertEquals(0, ImmutableList.copyOf(page.getValues()).size());
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIterableWithNoTenants() throws FirebaseAuthException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.<Tenant>of(),
        ListTenantsPage.END_OF_LIST);
    TestTenantSource source = new TestTenantSource(response);
    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    for (Tenant tenant : page.iterateAll()) {
      fail("Should not be able to iterate, but got: " + tenant);
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIteratorWithNoTenants() throws FirebaseAuthException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.<Tenant>of(),
        ListTenantsPage.END_OF_LIST);
    TestTenantSource source = new TestTenantSource(response);

    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    Iterator<Tenant> iterator = page.iterateAll().iterator();
    while (iterator.hasNext()) {
      fail("Should not be able to iterate");
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testRemove() throws FirebaseAuthException, IOException {
    ListTenantsResponse response = new ListTenantsResponse(
        ImmutableList.of(newTenant("tenant1")),
        ListTenantsPage.END_OF_LIST);
    TestTenantSource source = new TestTenantSource(response);

    ListTenantsPage page = new ListTenantsPage.PageFactory(source).create();
    Iterator<Tenant> iterator = page.iterateAll().iterator();
    while (iterator.hasNext()) {
      assertNotNull(iterator.next());
      try {
        iterator.remove();
      } catch (UnsupportedOperationException expected) {
        // expected
      }
    }
  }

  @Test(expected = NullPointerException.class)
  public void testNullSource() {
    new ListTenantsPage.PageFactory(null);
  }

  @Test
  public void testInvalidPageToken() throws IOException {
    TestTenantSource source = new TestTenantSource(1);
    try {
      new ListTenantsPage.PageFactory(source, 1000, "");
      fail("No error thrown for empty page token");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testInvalidMaxResults() throws IOException {
    TestTenantSource source = new TestTenantSource(1);
    try {
      new ListTenantsPage.PageFactory(source, 1001, "");
      fail("No error thrown for maxResult > 1000");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListTenantsPage.PageFactory(source, 0, "next");
      fail("No error thrown for maxResult = 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListTenantsPage.PageFactory(source, -1, "next");
      fail("No error thrown for maxResult < 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  private static Tenant newTenant(String tenantId) throws IOException {
    return Utils.getDefaultJsonFactory().fromString(
        String.format("{\"name\":\"%s\"}", tenantId), Tenant.class);
  }

  private static class TestTenantSource implements ListTenantsPage.TenantSource {

    private ListTenantsResponse response;
    private final List<String> calls = new ArrayList<>();

    TestTenantSource(int tenantCount) throws IOException {
      ImmutableList.Builder<Tenant> tenants = ImmutableList.builder();
      for (int i = 0; i < tenantCount; i++) {
        tenants.add(newTenant("tenant" + i));
      }
      this.response = new ListTenantsResponse(tenants.build(), ListTenantsPage.END_OF_LIST);
    }

    TestTenantSource(ListTenantsResponse response) {
      this.response = response;
    }

    @Override
    public ListTenantsResponse fetch(int maxResults, String pageToken) {
      calls.add(pageToken);
      return response;
    }
  }
}
