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

package com.google.firebase.remoteconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.remoteconfig.internal.TemplateResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

public class ListVersionsPageTest {

  @Test
  public void testSinglePage() throws FirebaseRemoteConfigException {
    TestVersionSource source = new TestVersionSource(3);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListVersionsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());

    ImmutableList<Version> versions = ImmutableList.copyOf(page.getValues());
    assertEquals(3, versions.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("1" + i, versions.get(i).getVersionNumber());
    }
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testMultiplePages() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.of(
                    newVersion("10"),
                    newVersion("11"),
                    newVersion("12")), "token");
    TestVersionSource source = new TestVersionSource(result);
    ListVersionsPage page1 = new ListVersionsPage.Factory(source).create();

    assertTrue(page1.hasNextPage());
    assertEquals("token", page1.getNextPageToken());
    ImmutableList<Version> versions = ImmutableList.copyOf(page1.getValues());
    assertEquals(3, versions.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("1" + i, versions.get(i).getVersionNumber());
    }

    result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.of(
                    newVersion("13"),
                    newVersion("14"),
                    newVersion("15")), ListVersionsPage.END_OF_LIST);
    source.result = result;
    ListVersionsPage page2 = page1.getNextPage();

    assertNotNull(page2);
    assertFalse(page2.hasNextPage());
    assertEquals(ListVersionsPage.END_OF_LIST, page2.getNextPageToken());
    versions = ImmutableList.copyOf(page2.getValues());
    assertEquals(3, versions.size());
    for (int i = 3; i < 6; i++) {
      assertEquals("1" + i, versions.get(i - 3).getVersionNumber());
    }

    assertEquals(2, source.calls.size());
    assertNull(source.calls.get(0));
    assertEquals("token", source.calls.get(1).getPageToken());

    // Should iterate all versions from both pages
    int iterations = 0;
    for (Version ignored : page1.iterateAll()) {
      iterations++;
    }
    assertEquals(6, iterations);
    assertEquals(3, source.calls.size());
    assertEquals("token", source.calls.get(2).getPageToken());

    // Should only iterate versions in the last page
    iterations = 0;
    for (Version ignored : page2.iterateAll()) {
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(3, source.calls.size());
  }

  @Test
  public void testListVersionsIterable() throws FirebaseRemoteConfigException {
    TestVersionSource source = new TestVersionSource(3);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    Iterable<Version> versions = page.iterateAll();

    int iterations = 0;
    for (Version version : versions) {
      assertEquals("1" + iterations, version.getVersionNumber());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    // Should result in a new iterator
    iterations = 0;
    for (Version version : versions) {
      assertEquals("1" + iterations, version.getVersionNumber());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testListVersionsIterator() throws FirebaseRemoteConfigException {
    TestVersionSource source = new TestVersionSource(3);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    Iterable<Version> versions = page.iterateAll();
    Iterator<Version> iterator = versions.iterator();
    int iterations = 0;
    while (iterator.hasNext()) {
      assertEquals("1" + iterations, iterator.next().getVersionNumber());
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
  public void testListVersionsPagedIterable() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.of(
                    newVersion("10"),
                    newVersion("11"),
                    newVersion("12")), "token");
    TestVersionSource source = new TestVersionSource(result);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    int iterations = 0;
    for (Version version : page.iterateAll()) {
      assertEquals("1" + iterations, version.getVersionNumber());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListVersionsPage.VersionsResultBatch(
                ImmutableList.of(
                        newVersion("13"),
                        newVersion("14"),
                        newVersion("15")), ListVersionsPage.END_OF_LIST);
        source.result = result;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1).getPageToken());
  }

  @Test
  public void testListVersionsPagedIterator() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.of(
                    newVersion("10"),
                    newVersion("11"),
                    newVersion("12")), "token");
    TestVersionSource source = new TestVersionSource(result);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    Iterator<Version> versions = page.iterateAll().iterator();
    int iterations = 0;
    while (versions.hasNext()) {
      assertEquals("1" + iterations, versions.next().getVersionNumber());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListVersionsPage.VersionsResultBatch(
                ImmutableList.of(
                        newVersion("13"),
                        newVersion("14"),
                        newVersion("15")), ListVersionsPage.END_OF_LIST);
        source.result = result;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1).getPageToken());
    assertFalse(versions.hasNext());
    try {
      versions.next();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void testPageWithNoVersions() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.<Version>of(),
            ListVersionsPage.END_OF_LIST);
    TestVersionSource source = new TestVersionSource(result);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();

    assertFalse(page.hasNextPage());
    assertEquals(ListVersionsPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());
    assertEquals(0, ImmutableList.copyOf(page.getValues()).size());
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIterableWithNoVersions() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.<Version>of(),
            ListVersionsPage.END_OF_LIST);
    TestVersionSource source = new TestVersionSource(result);
    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    for (Version version : page.iterateAll()) {
      fail("Should not be able to iterate, but got: " + version);
    }

    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIteratorWithNoVersions() throws FirebaseRemoteConfigException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.<Version>of(),
            ListVersionsPage.END_OF_LIST);
    TestVersionSource source = new TestVersionSource(result);

    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    Iterator<Version> iterator = page.iterateAll().iterator();

    assertFalse(iterator.hasNext());
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testRemove() throws FirebaseRemoteConfigException, IOException {
    ListVersionsPage.VersionsResultBatch result = new ListVersionsPage.VersionsResultBatch(
            ImmutableList.of(newVersion("10")),
            ListVersionsPage.END_OF_LIST);
    TestVersionSource source = new TestVersionSource(result);

    ListVersionsPage page = new ListVersionsPage.Factory(source).create();
    Iterator<Version> iterator = page.iterateAll().iterator();
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
    new ListVersionsPage.Factory(null);
  }

  private static Version newVersion(String versionNumber) {
    TemplateResponse.VersionResponse versionResponse = new TemplateResponse.VersionResponse()
            .setVersionNumber(versionNumber)
            .setUpdateTime("2020-11-15T06:57:26.342763941Z")
            .setUpdateOrigin("ADMIN_SDK")
            .setUpdateType("INCREMENTAL_UPDATE")
            .setUpdateUser(new TemplateResponse.UserResponse()
                    .setEmail("firebase-user@account.com")
                    .setName("dev-admin"))
            .setDescription("test version: " + versionNumber);
    return new Version(versionResponse);
  }

  private static class TestVersionSource implements ListVersionsPage.VersionSource {

    private ListVersionsPage.VersionsResultBatch result;
    private List<ListVersionsOptions> calls = new ArrayList<>();

    TestVersionSource(int versionCount) {
      ImmutableList.Builder<Version> versions = ImmutableList.builder();
      for (int i = 0; i < versionCount; i++) {
        versions.add(newVersion("1" + i));
      }
      this.result = new ListVersionsPage.VersionsResultBatch(versions.build(),
              ListVersionsPage.END_OF_LIST);
    }

    TestVersionSource(ListVersionsPage.VersionsResultBatch result) {
      this.result = result;
    }

    @Override
    public ListVersionsPage.VersionsResultBatch fetch(
            ListVersionsOptions listVersionsOptions) {
      calls.add(listVersionsOptions);
      return result;
    }
  }
}
