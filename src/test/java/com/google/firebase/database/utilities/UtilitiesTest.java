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

package com.google.firebase.database.utilities;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.core.ApiFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class UtilitiesTest {

  @Test
  public void testParseValidUrl() {
    ParsedUrl url = Utilities.parseUrl("https://test.firebaseio.com");
    assertEquals("test.firebaseio.com", url.repoInfo.host);
    assertTrue(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of(), url.path.asList());

    url = Utilities.parseUrl("https://test.firebaseio.com/");
    assertEquals("test.firebaseio.com", url.repoInfo.host);
    assertTrue(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of(), url.path.asList());

    url = Utilities.parseUrl("https://test.firebaseio.com:9090/");
    assertEquals("test.firebaseio.com:9090", url.repoInfo.host);
    assertTrue(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of(), url.path.asList());

    url = Utilities.parseUrl("http://test.firebaseio.com:9090/");
    assertEquals("test.firebaseio.com:9090", url.repoInfo.host);
    assertFalse(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of(), url.path.asList());

    url = Utilities.parseUrl("https://test.firebaseio.com/foo/bar");
    assertEquals("test.firebaseio.com", url.repoInfo.host);
    assertTrue(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of("foo", "bar"), url.path.asList());

    url = Utilities.parseUrl("https://test.firebaseio.com/foo//bar");
    assertEquals("test.firebaseio.com", url.repoInfo.host);
    assertTrue(url.repoInfo.isSecure());
    assertEquals("test", url.repoInfo.namespace);
    assertEquals(ImmutableList.of("foo", "bar"), url.path.asList());

    url = Utilities
        .parseUrl("https://firebaseio.com/path%20with%20spaces/?ns=random%20valid%20namespace");
    assertTrue(url.repoInfo.isSecure());
    assertEquals("random valid namespace", url.repoInfo.namespace);
    assertEquals("/path%20with%20spaces", url.path.toString());

    url = Utilities.parseUrl("http://test.firebaseio.com/+");
    assertEquals("/ ", url.path.toString());
  }

  @Test
  public void testParseInvalidUrl() {
    String[] urls = new String[]{"", "foo", "test.firebaseio.com"};
    for (String url : urls) {
      try {
        Utilities.parseUrl(url);
        fail("No error thrown for URL: '" + url + "'");
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testSplitIntoFrames() {
    String[] frames = Utilities.splitIntoFrames("foobar", 10);
    assertArrayEquals(new String[]{"foobar"}, frames);

    frames = Utilities.splitIntoFrames("foobar", 6);
    assertArrayEquals(new String[]{"foobar"}, frames);

    frames = Utilities.splitIntoFrames("foobar", 3);
    assertArrayEquals(new String[]{"foo", "bar"}, frames);

    frames = Utilities.splitIntoFrames("foobar", 2);
    assertArrayEquals(new String[]{"fo", "ob", "ar"}, frames);
  }

  @Test
  public void testCastOrNull() {
    Object o = "foo";
    String stringValue = Utilities.castOrNull(o, String.class);
    assertEquals(o, stringValue);

    Integer intValue = Utilities.castOrNull(o, Integer.class);
    assertNull(intValue);
  }

  @Test
  public void getOrNull() {
    Object map = ImmutableMap.of("foo", 1, "bar", "value");
    String stringValue = Utilities.getOrNull(map, "bar", String.class);
    assertEquals("value", stringValue);

    stringValue = Utilities.getOrNull(map, "baz", String.class);
    assertNull(stringValue);

    Integer intValue = Utilities.getOrNull(map, "bar", Integer.class);
    assertNull(intValue);

    assertNull(Utilities.getOrNull(null, "foo", String.class));
  }

  @Test
  public void testHardAssert() {
    try {
      Utilities.hardAssert(false);
      fail("No error thrown for hardAssert failure");
    } catch (AssertionError expected) {
      // expected
    }

    // Should not throw
    Utilities.hardAssert(true);
  }

  @Test
  public void testWrapOnComplete() throws Exception {
    Pair<ApiFuture<Void>, DatabaseReference.CompletionListener> result =
        Utilities.wrapOnComplete(null);
    assertNotNull(result.getFirst());
    assertNotNull(result.getSecond());
    assertFalse(result.getFirst().isDone());

    result.getSecond().onComplete(null, null);
    assertTrue(result.getFirst().isDone());
    assertNull(result.getFirst().get());
  }

  @Test
  public void testWrapOnCompleteErrorResult() throws InterruptedException {
    Pair<ApiFuture<Void>, DatabaseReference.CompletionListener> result =
        Utilities.wrapOnComplete(null);
    assertNotNull(result.getFirst());
    assertNotNull(result.getSecond());
    assertFalse(result.getFirst().isDone());

    result.getSecond().onComplete(DatabaseError.fromStatus("test error"), null);
    try {
      result.getFirst().get();
    } catch (ExecutionException e) {
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void testWrapOnCompleteExplicit() {
    CompletionListener listener = new CompletionListener() {
      @Override
      public void onComplete(DatabaseError error, DatabaseReference ref) {

      }
    };
    Pair<ApiFuture<Void>, DatabaseReference.CompletionListener> result =
        Utilities.wrapOnComplete(listener);
    assertNull(result.getFirst());
    assertSame(listener, result.getSecond());
  }

  @Test
  public void testExtractParamsFromUrl() throws UnsupportedEncodingException {
    Map<String, String> params = Utilities.getQueryParamsMap("abc=213&qpf=2312&xyz=true&qpf=hi");
    assertEquals("213", params.get("abc"));
    assertEquals("2312,hi", params.get("qpf"));
    assertEquals("true", params.get("xyz"));

    params = Utilities.getQueryParamsMap(
        "q=a%3D2%26b%3D3&oq=a%3D2%26b%3D3&aqs=chrome..69i57j0l5.4023j0j7&sourceid=chrome&ie=UTF-8");
    assertEquals("a=2&b=3", params.get("q"));
    assertEquals("a=2&b=3", params.get("oq"));
    assertEquals("chrome", params.get("sourceid"));

    params = Utilities.getQueryParamsMap("a=%3F%3F%3F&b=%3D%26%3D");
    assertEquals("???", params.get("a"));
    assertEquals("=&=", params.get("b"));
  }
}
