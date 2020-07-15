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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.ListOidcProviderConfigsResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

public class ListProviderConfigsPageTest {

  @Test
  public void testSinglePage() throws FirebaseAuthException, IOException {
    TestProviderConfigSource source = new TestProviderConfigSource(3);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListProviderConfigsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());

    ImmutableList<OidcProviderConfig> providerConfigs = ImmutableList.copyOf(page.getValues());
    assertEquals(3, providerConfigs.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("oidc.provider-id-" + i, providerConfigs.get(i).getProviderId());
    }
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testMultiplePages() throws FirebaseAuthException, IOException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.of(
          newOidcProviderConfig("oidc.provider-id-0"),
          newOidcProviderConfig("oidc.provider-id-1"),
          newOidcProviderConfig("oidc.provider-id-2")),
        "token");
    TestProviderConfigSource source = new TestProviderConfigSource(response);
    ListProviderConfigsPage<OidcProviderConfig> page1 =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    assertTrue(page1.hasNextPage());
    assertEquals("token", page1.getNextPageToken());
    ImmutableList<OidcProviderConfig> providerConfigs = ImmutableList.copyOf(page1.getValues());
    assertEquals(3, providerConfigs.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("oidc.provider-id-" + i, providerConfigs.get(i).getProviderId());
    }

    response = new ListOidcProviderConfigsResponse(
        ImmutableList.of(
          newOidcProviderConfig("oidc.provider-id-3"),
          newOidcProviderConfig("oidc.provider-id-4"),
          newOidcProviderConfig("oidc.provider-id-5")),
        ListProviderConfigsPage.END_OF_LIST);
    source.response = response;
    ListProviderConfigsPage<OidcProviderConfig> page2 = page1.getNextPage();
    assertFalse(page2.hasNextPage());
    assertEquals(ListProviderConfigsPage.END_OF_LIST, page2.getNextPageToken());
    providerConfigs = ImmutableList.copyOf(page2.getValues());
    assertEquals(3, providerConfigs.size());
    for (int i = 3; i < 6; i++) {
      assertEquals("oidc.provider-id-" + i, providerConfigs.get(i - 3).getProviderId());
    }

    assertEquals(2, source.calls.size());
    assertNull(source.calls.get(0));
    assertEquals("token", source.calls.get(1));

    // Should iterate all provider configs from both pages
    int iterations = 0;
    for (OidcProviderConfig providerConfig : page1.iterateAll()) {
      iterations++;
    }
    assertEquals(6, iterations);
    assertEquals(3, source.calls.size());
    assertEquals("token", source.calls.get(2));

    // Should only iterate provider configs in the last page
    iterations = 0;
    for (OidcProviderConfig providerConfig : page2.iterateAll()) {
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(3, source.calls.size());
  }

  @Test
  public void testListProviderConfigsIterable() throws FirebaseAuthException, IOException {
    TestProviderConfigSource source = new TestProviderConfigSource(3);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    Iterable<OidcProviderConfig> providerConfigs = page.iterateAll();

    int iterations = 0;
    for (OidcProviderConfig providerConfig : providerConfigs) {
      assertEquals("oidc.provider-id-" + iterations, providerConfig.getProviderId());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    // Should result in a new iterator
    iterations = 0;
    for (OidcProviderConfig providerConfig : providerConfigs) {
      assertEquals("oidc.provider-id-" + iterations, providerConfig.getProviderId());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testListProviderConfigsIterator() throws FirebaseAuthException, IOException {
    TestProviderConfigSource source = new TestProviderConfigSource(3);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    Iterable<OidcProviderConfig> providerConfigs = page.iterateAll();
    Iterator<OidcProviderConfig> iterator = providerConfigs.iterator();
    int iterations = 0;
    while (iterator.hasNext()) {
      assertEquals("oidc.provider-id-" + iterations, iterator.next().getProviderId());
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
  public void testListProviderConfigsPagedIterable() throws FirebaseAuthException, IOException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.of(
          newOidcProviderConfig("oidc.provider-id-0"),
          newOidcProviderConfig("oidc.provider-id-1"),
          newOidcProviderConfig("oidc.provider-id-2")),
        "token");
    TestProviderConfigSource source = new TestProviderConfigSource(response);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    int iterations = 0;
    for (OidcProviderConfig providerConfig : page.iterateAll()) {
      assertEquals("oidc.provider-id-" + iterations, providerConfig.getProviderId());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        response = new ListOidcProviderConfigsResponse(
            ImmutableList.of(
              newOidcProviderConfig("oidc.provider-id-3"),
              newOidcProviderConfig("oidc.provider-id-4"),
              newOidcProviderConfig("oidc.provider-id-5")),
            ListProviderConfigsPage.END_OF_LIST);
        source.response = response;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
  }

  @Test
  public void testListProviderConfigsPagedIterator() throws FirebaseAuthException, IOException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.of(
          newOidcProviderConfig("oidc.provider-id-0"),
          newOidcProviderConfig("oidc.provider-id-1"),
          newOidcProviderConfig("oidc.provider-id-2")),
        "token");
    TestProviderConfigSource source = new TestProviderConfigSource(response);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    Iterator<OidcProviderConfig> providerConfigs = page.iterateAll().iterator();
    int iterations = 0;
    while (providerConfigs.hasNext()) {
      assertEquals("oidc.provider-id-" + iterations, providerConfigs.next().getProviderId());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        response = new ListOidcProviderConfigsResponse(
            ImmutableList.of(
              newOidcProviderConfig("oidc.provider-id-3"),
              newOidcProviderConfig("oidc.provider-id-4"),
              newOidcProviderConfig("oidc.provider-id-5")),
            ListProviderConfigsPage.END_OF_LIST);
        source.response = response;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
    assertFalse(providerConfigs.hasNext());
    try {
      providerConfigs.next();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void testPageWithNoproviderConfigs() throws FirebaseAuthException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.<OidcProviderConfig>of(), ListProviderConfigsPage.END_OF_LIST);
    TestProviderConfigSource source = new TestProviderConfigSource(response);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListProviderConfigsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());
    assertEquals(0, ImmutableList.copyOf(page.getValues()).size());
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIterableWithNoproviderConfigs() throws FirebaseAuthException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.<OidcProviderConfig>of(), ListProviderConfigsPage.END_OF_LIST);
    TestProviderConfigSource source = new TestProviderConfigSource(response);
    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    for (OidcProviderConfig providerConfig : page.iterateAll()) {
      fail("Should not be able to iterate, but got: " + providerConfig);
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIteratorWithNoproviderConfigs() throws FirebaseAuthException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.<OidcProviderConfig>of(), ListProviderConfigsPage.END_OF_LIST);
    TestProviderConfigSource source = new TestProviderConfigSource(response);

    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    Iterator<OidcProviderConfig> iterator = page.iterateAll().iterator();
    while (iterator.hasNext()) {
      fail("Should not be able to iterate");
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testRemove() throws FirebaseAuthException, IOException {
    ListOidcProviderConfigsResponse response = new ListOidcProviderConfigsResponse(
        ImmutableList.of(newOidcProviderConfig("oidc.provider-id-1")),
        ListProviderConfigsPage.END_OF_LIST);
    TestProviderConfigSource source = new TestProviderConfigSource(response);

    ListProviderConfigsPage<OidcProviderConfig> page =
        new ListProviderConfigsPage.Factory<OidcProviderConfig>(source).create();
    Iterator<OidcProviderConfig> iterator = page.iterateAll().iterator();
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
    new ListProviderConfigsPage.Factory<OidcProviderConfig>(null);
  }

  @Test
  public void testInvalidPageToken() throws IOException {
    TestProviderConfigSource source = new TestProviderConfigSource(1);
    try {
      new ListProviderConfigsPage.Factory<OidcProviderConfig>(source, 1000, "");
      fail("No error thrown for empty page token");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testInvalidMaxResults() throws IOException {
    TestProviderConfigSource source = new TestProviderConfigSource(1);
    try {
      new ListProviderConfigsPage.Factory<OidcProviderConfig>(source, 1001, "");
      fail("No error thrown for maxResult > 1000");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListProviderConfigsPage.Factory<OidcProviderConfig>(source, 0, "next");
      fail("No error thrown for maxResult = 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListProviderConfigsPage.Factory<OidcProviderConfig>(source, -1, "next");
      fail("No error thrown for maxResult < 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  private static OidcProviderConfig newOidcProviderConfig(String providerConfigId)
      throws IOException {
    return Utils.getDefaultJsonFactory().fromString(
        String.format("{\"name\":\"%s\"}", providerConfigId), OidcProviderConfig.class);
  }

  private static class TestProviderConfigSource
      implements ListProviderConfigsPage.ProviderConfigSource<OidcProviderConfig> {

    private ListOidcProviderConfigsResponse response;
    private final List<String> calls = new ArrayList<>();

    TestProviderConfigSource(int providerConfigCount) throws IOException {
      ImmutableList.Builder<OidcProviderConfig> providerConfigs = ImmutableList.builder();
      for (int i = 0; i < providerConfigCount; i++) {
        providerConfigs.add(newOidcProviderConfig("oidc.provider-id-" + i));
      }
      this.response = new ListOidcProviderConfigsResponse(
        providerConfigs.build(), ListProviderConfigsPage.END_OF_LIST);
    }

    TestProviderConfigSource(ListOidcProviderConfigsResponse response) {
      this.response = response;
    }

    @Override
    public ListOidcProviderConfigsResponse fetch(int maxResults, String pageToken) {
      calls.add(pageToken);
      return response;
    }
  }
}
