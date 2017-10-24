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
  public void testListUsersIterable() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);

    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterable<ExportedUserRecord> users = page.iterateAll();

    int iterations = 0;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));

    // Should result in a new iterator
    iterations = 0;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
    }
    assertEquals(3, iterations);
    assertEquals(1, source.calls.size());
    assertNull(source.calls.get(0));
  }

  @Test
  public void testListUsersIterator() throws IOException {
    ListUsersResult result = new ListUsersResult(
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        ListUsersPage.END_OF_LIST);
    TestUserSource source = new TestUserSource(result);

    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterable<ExportedUserRecord> users = page.iterateAll();
    Iterator<ExportedUserRecord> iterator = users.iterator();
    int iterations = 0;
    while (iterator.hasNext()) {
      iterations++;
      assertEquals("user" + iterations, iterator.next().getUid());
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
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        "token");
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterable<ExportedUserRecord> users = page.iterateAll();
    int iterations = 0;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListUsersResult(
            ImmutableList.of(newUser("user4"), newUser("user5"), newUser("user6")),
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
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        "token");
    TestUserSource source = new TestUserSource(result);
    ListUsersPage page = new ListUsersPage.PageFactory(source).create();
    Iterator<ExportedUserRecord> users = page.iterateAll().iterator();
    int iterations = 0;
    while (users.hasNext()) {
      iterations++;
      assertEquals("user" + iterations, users.next().getUid());
      if (iterations == 3) {
        assertEquals(1, source.calls.size());
        assertNull(source.calls.get(0));
        result = new ListUsersResult(
            ImmutableList.of(newUser("user4"), newUser("user5"), newUser("user6")),
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

  private static ExportedUserRecord newUser(String uid) throws IOException {
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    DownloadAccountResponse.User parsed = jsonFactory.fromString(
        String.format("{\"localId\":\"%s\"}", uid), DownloadAccountResponse.User.class);
    return new ExportedUserRecord(parsed);
  }

  private static class TestUserSource implements ListUsersPage.UserSource {

    private ListUsersResult result;
    private List<String> calls = new ArrayList<>();

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
