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
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.Test;

public class UserIterableTest {

  @Test
  public void testListUsers() throws IOException {
    UserFetcher.FetchResult result = new UserFetcher.FetchResult(
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        null);
    TestUserFetcher downloader = new TestUserFetcher(result);

    int iterations = 0;
    UserIterable iterable = new UserIterable(downloader);
    for (ExportedUserRecord user : iterable) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
    }
    assertEquals(3, iterations);
    assertEquals(1, downloader.calls);

    for (ExportedUserRecord user : iterable) {
      fail("Should not iterate any further, but got: " + user);
    }
    assertEquals(1, downloader.calls);
  }

  @Test
  public void testListUsersPagedIterator() throws IOException {
    UserFetcher.FetchResult result = new UserFetcher.FetchResult(
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        "token");
    TestUserFetcher downloader = new TestUserFetcher(result);
    Iterator<ExportedUserRecord> users = new UserIterable(downloader).iterator();
    for (int i = 1; i <= 3; i++) {
      assertEquals("user" + i, users.next().getUid());
    }
    assertEquals(1, downloader.calls);

    result = new UserFetcher.FetchResult(
        ImmutableList.of(newUser("user4"), newUser("user5"), newUser("user6")),
        null);
    downloader.result = result;
    for (int i = 4; i <= 6; i++) {
      assertEquals("user" + i, users.next().getUid());
    }
    assertEquals(2, downloader.calls);

    assertFalse(users.hasNext());
    try {
      users.next();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void testListUsersPagedIterable() throws IOException {
    UserFetcher.FetchResult result = new UserFetcher.FetchResult(
        ImmutableList.of(newUser("user1"), newUser("user2"), newUser("user3")),
        "token");
    TestUserFetcher downloader = new TestUserFetcher(result);
    Iterable<ExportedUserRecord> users = new UserIterable(downloader);
    int iterations = 0;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
      if (iterations == 3) {
        break;
      }
    }
    assertEquals(1, downloader.calls);

    result = new UserFetcher.FetchResult(
        ImmutableList.of(newUser("user4"), newUser("user5"), newUser("user6")),
        null);
    downloader.result = result;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
    }
    assertEquals(2, downloader.calls);
    assertEquals(6, iterations);
  }

  private static ExportedUserRecord newUser(String uid) throws IOException {
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    DownloadAccountResponse.ExportedUser parsed = jsonFactory.fromString(
        String.format("{\"localId\":\"%s\"}", uid),
        DownloadAccountResponse.ExportedUser.class);
    return new ExportedUserRecord(parsed);
  }

  private static class TestUserFetcher implements UserFetcher {

    private FetchResult result;
    private int calls = 0;

    TestUserFetcher(FetchResult result) {
      this.result = result;
    }

    @Override
    public FetchResult fetch(int maxResults, String pageToken) throws Exception {
      calls++;
      return result;
    }
  }
}
