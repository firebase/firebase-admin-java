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
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.FirebaseUserManager.PageToken;
import com.google.firebase.auth.FirebaseUserManager.UserAccountDownloader;
import com.google.firebase.auth.internal.DownloadAccountResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.Test;

public class UserIterableTest {

  @Test
  public void testListUsers() throws IOException {
    DownloadAccountResponse response = parse(
        ImmutableList.<Object>of(
            ImmutableMap.of("localId", "user1"),
            ImmutableMap.of("localId", "user2"),
            ImmutableMap.of("localId", "user3")),
        null);
    TestUserDownloader downloader = new TestUserDownloader(response);

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
  public void testListUsersIteratorPagedResponse() throws IOException {
    DownloadAccountResponse response = parse(
        ImmutableList.<Object>of(
            ImmutableMap.of("localId", "user1"),
            ImmutableMap.of("localId", "user2"),
            ImmutableMap.of("localId", "user3")),
        "token");
    TestUserDownloader downloader = new TestUserDownloader(response);
    Iterator<ExportedUserRecord> users = new UserIterable(downloader).iterator();
    for (int i = 1; i <= 3; i++) {
      assertEquals("user" + i, users.next().getUid());
    }
    assertEquals(1, downloader.calls);

    response = parse(
        ImmutableList.<Object>of(
            ImmutableMap.of("localId", "user4"),
            ImmutableMap.of("localId", "user5"),
            ImmutableMap.of("localId", "user6")),
        null);
    downloader.response = response;
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
  public void testListUsersIterablePagedResponse() throws IOException {
    DownloadAccountResponse response = parse(
        ImmutableList.<Object>of(
            ImmutableMap.of("localId", "user1"),
            ImmutableMap.of("localId", "user2"),
            ImmutableMap.of("localId", "user3")),
        "token");
    TestUserDownloader downloader = new TestUserDownloader(response);
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

    response = parse(
        ImmutableList.<Object>of(
            ImmutableMap.of("localId", "user4"),
            ImmutableMap.of("localId", "user5"),
            ImmutableMap.of("localId", "user6")),
        null);
    downloader.response = response;
    for (ExportedUserRecord user : users) {
      iterations++;
      assertEquals("user" + iterations, user.getUid());
    }
    assertEquals(2, downloader.calls);
    assertEquals(6, iterations);
  }

  private DownloadAccountResponse parse(List<Object> users, String pageToken) throws IOException {
    Map<String, Object> data = new HashMap<>();
    data.put("users", users);
    if (pageToken != null) {
      data.put("nextPageToken", pageToken);
    }
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    String json = jsonFactory.toString(data);
    return jsonFactory.fromInputStream(new ByteArrayInputStream(json.getBytes()),
        DownloadAccountResponse.class);
  }

  private static class TestUserDownloader implements UserAccountDownloader {

    private DownloadAccountResponse response;
    private int calls = 0;

    TestUserDownloader(DownloadAccountResponse response) {
      this.response = response;
    }

    @Override
    public DownloadAccountResponse download(int maxResults, PageToken pageToken) throws Exception {
      this.calls++;
      return response;
    }
  }
}
