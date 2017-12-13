/*
 * Copyright 2017 Google Inc.
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
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.ListUsersPage.ListUsersResult;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

public class ListUsersPageTest {

  @Test
  public void testSinglePage() throws IOException {
    TestUserSource source = new TestUserSource(3);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListUsersPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());

    ImmutableList<ExportedUserRecord> users = ImmutableList.copyOf(page.getValues());
    assertEquals(3, users.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("user" + i, users.get(i).getUid());
    }
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testMultiplePages() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user0"), newUser("user1"), newUser("user2")),
        "token");
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page1 = new ListUsersPage.PageFactory(source).create();
    assertTrue(page1.hasNextPage());
    assertEquals("token", page1.getNextPageToken());
    ImmutableList<ExportedUserRecord> users = ImmutableList.copyOf(page1.getValues());
    assertEquals(3, users.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("user" + i, users.get(i).getUid());
    }

    result = new ListUsersResult(
        ImmutableList.of(newUser("user3"), newUser("user4"), newUser("user5")),
        ListUsersPage.END_OF_LIST);
    source.result = result;
    ListUsersPage page2 = page1.getNextPage();
    assertFalse(page2.hasNextPage());
    assertEquals(ListUsersPage.END_OF_LIST, page2.getNextPageToken());
    users = ImmutableList.copyOf(page2.getValues());
    assertEquals(3, users.size());
    for (int i = 3; i < 6; i++) {
      assertEquals("user" + i, users.get(i - 3).getUid());
    }

    assertEquals(2, source.calls.size());
    assertNull(source.calls.get(0));
    assertEquals("token", source.calls.get(1));

    // Should iterate all users from both pages
    int iterations = 0;
    for (ExportedUserRecord user : page1.iterateAll()) {
      iterations++;
    }
    assertEquals(6, iterations);
    assertEquals(3, source.calls.size());
    assertEquals("token", source.calls.get(2));

    // Should only iterate users in the last page
    iterations = 0;
    for (ExportedUserRecord user : page2.iterateAll()) {
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(3, source.calls.size());
  }

  @Test
  public void testListUsersIterable() throws IOException {
    TestUserSource source = new TestUserSource(3);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterable<ExportedUserRecord> users = page.iterateAll();

    int iterations = 0;
    for (ExportedUserRecord user : users) {
      assertEquals("user" + iterations, user.getUid());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    // Should result in a new iterator
    iterations = 0;
    for (ExportedUserRecord user : users) {
      assertEquals("user" + iterations, user.getUid());
      iterations++;
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testListUsersIterator() throws IOException {
    TestUserSource source = new TestUserSource(3);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterable<ExportedUserRecord> users = page.iterateAll();
    Iterator<ExportedUserRecord> iterator = users.iterator();
    int iterations = 0;
    while (iterator.hasNext()) {
      assertEquals("user" + iterations, iterator.next().getUid());
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
  public void testListUsersPagedIterable() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user0"), newUser("user1"), newUser("user2")),
        "token");
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    int iterations = 0;
    for (ExportedUserRecord user : page.iterateAll()) {
      assertEquals("user" + iterations, user.getUid());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListUsersResult(
            ImmutableList.of(newUser("user3"), newUser("user4"), newUser("user5")),
            ListUsersPage.END_OF_LIST);
        source.result = result;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
  }

  @Test
  public void testListUsersPagedIterator() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user0"), newUser("user1"), newUser("user2")),
        "token");
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterator<ExportedUserRecord> users = page.iterateAll().iterator();
    int iterations = 0;
    while (users.hasNext()) {
      assertEquals("user" + iterations, users.next().getUid());
      iterations++;
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListUsersResult(
            ImmutableList.of(newUser("user3"), newUser("user4"), newUser("user5")),
            ListUsersPage.END_OF_LIST);
        source.result = result;
      }
    }

    assertEquals(6, iterations);
    assertEquals(2, source.calls.size());
    assertEquals("token", source.calls.get(1));
    assertFalse(users.hasNext());
    try {
      users.next();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void testPageWithNoUsers() {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.<ExportedUserRecord>of(),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    assertFalse(page.hasNextPage());
    assertEquals(ListUsersPage.END_OF_LIST, page.getNextPageToken());
    assertNull(page.getNextPage());
    assertEquals(0, ImmutableList.copyOf(page.getValues()).size());
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIterableWithNoUsers() {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.<ExportedUserRecord>of(),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    for (ExportedUserRecord user : page.iterateAll()) {
      fail("Should not be able to iterate, but got: " + user);
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testIteratorWithNoUsers() {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.<ExportedUserRecord>of(),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);

    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterator<ExportedUserRecord> iterator = page.iterateAll().iterator();
    while (iterator.hasNext()) {
      fail("Should not be able to iterate");
    }
    assertEquals(1, source.calls.size());
  }

  @Test
  public void testRemove() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user1")),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);

    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterator<ExportedUserRecord> iterator = page.iterateAll().iterator();
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
    new ListUsersPage.PageFactory(null);
  }

  @Test
  public void testInvalidPageToken() throws IOException {
    TestUserSource source = new TestUserSource(1);
    try {
      new ListUsersPage.PageFactory(source, 1000, "");
      fail("No error thrown for empty page token");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testInvalidMaxResults() throws IOException {
    TestUserSource source = new TestUserSource(1);
    try {
      new ListUsersPage.PageFactory(source, 1001, "");
      fail("No error thrown for maxResult > 1000");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListUsersPage.PageFactory(source, 0, "next");
      fail("No error thrown for maxResult = 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      new ListUsersPage.PageFactory(source, -1, "next");
      fail("No error thrown for maxResult < 0");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  private static ExportedUserRecord newUser(String uid) throws IOException {
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    DownloadAccountResponse.User parsed = jsonFactory.fromString(
        String.format("{\"localId\":\"%s\"}", uid), DownloadAccountResponse.User.class);
    return new ExportedUserRecord(parsed, jsonFactory);
  }

  private static class TestUserSource implements ListUsersPage.UserSource {

    private ListUsersResult result;
    private List<String> calls = new ArrayList<>();

    TestUserSource(int userCount) throws IOException {
      ImmutableList.Builder<ExportedUserRecord> users = ImmutableList.builder();
      for (int i = 0; i < userCount; i++) {
        users.add(newUser("user" + i));
      }
      this.result = new ListUsersResult(users.build(), ListUsersPage.END_OF_LIST);
    }

    TestUserSource(ListUsersResult result) {
      this.result = result;
    }

    @Override
    public ListUsersResult fetch(int maxResults, String pageToken) {
      calls.add(pageToken);
      return result;
    }
  }
}
