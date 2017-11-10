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

package com.google.firebase.database;

import static com.google.firebase.database.TestHelpers.mockRepo;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.snapshot.NodeUtilities;
import java.util.Map;
import org.junit.Test;
import org.mockito.Mockito;

public class OnDisconnectTest {

  private static final Path path = new Path("foo");

  @Test
  public void testSetValue() throws Exception {
    Repo repo = mockRepo();
    OnDisconnect reference = new OnDisconnect(repo, path);
    reference.setValueAsync("value");
    reference.setValue("value", (CompletionListener) null);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectSetValue(
            Mockito.same(path),
            Mockito.eq(NodeUtilities.NodeFromJSON("value")),
            Mockito.any(CompletionListener.class));
  }

  @Test
  public void testSetValueWithPriority() throws Exception {
    Repo repo = mockRepo();
    OnDisconnect reference = new OnDisconnect(repo, path);
    reference.setValueAsync("value", 10);
    reference.setValue("value", 10, null);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectSetValue(
            Mockito.same(path),
            Mockito.eq(NodeUtilities.NodeFromJSON(ImmutableMap.of(
                ".value", "value", ".priority", 10))),
            Mockito.any(CompletionListener.class));

    repo = mockRepo();
    reference = new OnDisconnect(repo, path);
    reference.setValueAsync("value", "p");
    reference.setValue("value", "p", null);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectSetValue(
            Mockito.same(path),
            Mockito.eq(NodeUtilities.NodeFromJSON(ImmutableMap.of(
                ".value", "value", ".priority", "p"))),
            Mockito.any(CompletionListener.class));

    repo = mockRepo();
    reference = new OnDisconnect(repo, path);
    reference.setValue("value", ImmutableMap.of(), null);
    Mockito.verify(repo, times(1))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(1))
        .onDisconnectSetValue(
            Mockito.same(path),
            Mockito.eq(NodeUtilities.NodeFromJSON("value")),
            Mockito.any(CompletionListener.class));
  }

  @Test
  public void testUpdateChildren() throws Exception {
    Repo repo = mockRepo();
    OnDisconnect reference = new OnDisconnect(repo, path);
    try {
      reference.updateChildrenAsync(null);
      fail("No error thrown for null update");
    } catch (NullPointerException expected) {
      // expected
    }

    ImmutableMap<String, Object> update = ImmutableMap.<String, Object>of("foo", "bar");
    reference.updateChildrenAsync(update);
    reference.updateChildren(update, null);
    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectUpdate(
            Mockito.same(path),
            Mockito.any(Map.class),
            Mockito.any(CompletionListener.class),
            Mockito.same(update));
  }

  @Test
  public void testRemoveValue() throws Exception {
    Repo repo = mockRepo();
    OnDisconnect reference = new OnDisconnect(repo, path);
    reference.removeValueAsync();
    reference.removeValue(null);

    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectSetValue(
            Mockito.same(path),
            Mockito.eq(NodeUtilities.NodeFromJSON(null)),
            Mockito.any(CompletionListener.class));
  }

  @Test
  public void testCancel() throws Exception {
    Repo repo = mockRepo();
    OnDisconnect reference = new OnDisconnect(repo, path);
    reference.cancelAsync();
    reference.cancel(null);

    Mockito.verify(repo, times(2))
        .scheduleNow(Mockito.any(Runnable.class));
    Mockito.verify(repo, times(2))
        .onDisconnectCancel(
            Mockito.same(path),
            Mockito.any(CompletionListener.class));
  }
}
