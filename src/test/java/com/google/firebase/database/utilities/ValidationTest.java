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

import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.core.Path;
import java.util.Map;
import org.junit.Test;

public class ValidationTest {

  @Test
  public void testValidPathString() {
    String[] validPaths = new String[]{
        "foo", "foo_", "foo-", "/foo", "/foo/",
        "/foo/bar", "/foo/bar/"
    };
    for (String path : validPaths) {
      // Should not throw
      Validation.validatePathString(path);
    }
  }

  @Test
  public void testInvalidPathString() {
    String[] invalidPaths = new String[]{
      "foo.", "foo#", "foo$", "foo[", "foo]", ".info",
    };
    for (String path : invalidPaths) {
      try {
        Validation.validatePathString(path);
        fail("No error thrown for invalid path: " + path);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testValidRootPathString() {
    String[] validPaths = new String[]{
        ".info", ".info/foo", "/.info/foo"
    };
    for (String path : validPaths) {
      // Should not throw
      Validation.validateRootPathString(path);
    }
  }

  @Test
  public void testInvalidRootPathString() {
    String[] invalidPaths = new String[]{
        ".info/foo.", ".info/foo#", ".info/foo$", ".info/foo[", ".info/foo]"
    };
    for (String path : invalidPaths) {
      try {
        Validation.validateRootPathString(path);
        fail("No error thrown for invalid path: " + path);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testNullableKey() {
    // Should not throw
    Validation.validateNullableKey(null);
  }

  @Test
  public void testInvalidNullableKey() {
    String[] invalidKeys = new String[]{
        "foo.", "foo#", "foo$", "foo[", "foo]",
    };
    for (String key : invalidKeys) {
      try {
        Validation.validateNullableKey(key);
        fail("No error thrown for invalid nullable key: " + key);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testWritableKey() {
    String[] invalidKeys = new String[]{
        null, "", ".info", ".foo", "foo#", "foo$", "foo[", "foo]"
    };
    for (String key : invalidKeys) {
      try {
        Validation.validateWritableKey(key);
        fail("No error thrown for non-writable key: " + key);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testWritablePath() {
    String[] paths = new String[]{
        "foo", "foo/bar", "foo/bar/", "/foo/bar", "/foo/bar/"
    };
    for (String pathString : paths) {
      Path path = new Path(pathString);
      Validation.validateWritablePath(path);
    }
  }

  @Test
  public void testNonWritablePath() {
    String[] invalidPaths = new String[]{
        ".info/foo", ".foo/bar"
    };
    for (String pathString : invalidPaths) {
      Path path = new Path(pathString);
      try {
        Validation.validateWritablePath(path);
        fail("No error thrown for non-writable path: " + pathString);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

  @Test
  public void testUpdate() {
    Map[] updates = new Map[]{
        ImmutableMap.of("foo", "value"),
        ImmutableMap.of("foo", ""),
        ImmutableMap.of("foo", 10D),
        ImmutableMap.of(".foo", "foo"),
        ImmutableMap.of("foo", "value", "bar", "value"),
    };
    Path path = new Path("path");
    for (Map map : updates) {
      Validation.parseAndValidateUpdate(path, map);
    }
  }

  @Test
  public void testInvalidUpdate() {
    Map[] invalidUpdates = new Map[]{
        ImmutableMap.of(".sv", "foo"),
        ImmutableMap.of(".value", "foo"),
        ImmutableMap.of(".priority", ImmutableMap.of("a", "b")),
        ImmutableMap.of("foo", "value", "foo/bar", "value"),
        ImmutableMap.of("foo", Double.POSITIVE_INFINITY),
        ImmutableMap.of("foo", Double.NEGATIVE_INFINITY),
        ImmutableMap.of("foo", Double.NaN),
    };
    Path path = new Path("path");
    for (Map map : invalidUpdates) {
      try {
        Validation.parseAndValidateUpdate(path, map);
        fail("No error thrown for invalid update: " + map);
      } catch (DatabaseException expected) {
        // expected
      }
    }
  }

}
