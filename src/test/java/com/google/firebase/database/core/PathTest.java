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

package com.google.firebase.database.core;

import org.junit.Assert;
import org.junit.Test;

public class PathTest {

  @Test
  public void containsTest() {
    Assert.assertTrue(new Path("/").contains(new Path("/a/b/c")));
    Assert.assertTrue(new Path("/a").contains(new Path("/a/b/c")));
    Assert.assertTrue(new Path("/a/b").contains(new Path("/a/b/c")));
    Assert.assertTrue(new Path("/a/b/c").contains(new Path("/a/b/c")));

    Assert.assertFalse(new Path("/a/b/c").contains(new Path("/a/b")));
    Assert.assertFalse(new Path("/a/b/c").contains(new Path("/a")));
    Assert.assertFalse(new Path("/a/b/c").contains(new Path("/")));

    Assert.assertTrue(new Path("/a/b/c").popFront().contains(new Path("/b/c")));
    Assert.assertTrue(new Path("/a/b/c").popFront().contains(new Path("/b/c/d")));

    Assert.assertFalse(new Path("/a/b/c").contains(new Path("/b/c")));
    Assert.assertFalse(new Path("/a/b/c").contains(new Path("/a/c/b")));

    Assert.assertFalse(new Path("/a/b/c").popFront().contains(new Path("/a/b/c")));
    Assert.assertTrue(new Path("/a/b/c").popFront().contains(new Path("/b/c")));
    Assert.assertTrue(new Path("/a/b/c").popFront().contains(new Path("/b/c/d")));
  }

  @Test
  public void popFront() {
    Assert.assertEquals(new Path("/a/b/c").popFront(), new Path("/b/c"));
    Assert.assertEquals(new Path("/a/b/c").popFront().popFront(), new Path("/c"));
    Assert.assertEquals(new Path("/a/b/c").popFront().popFront().popFront(), new Path("/"));
    Assert.assertEquals(
        new Path("/a/b/c").popFront().popFront().popFront().popFront(), new Path("/"));
  }

  @Test
  public void parent() {
    Assert.assertEquals(new Path("/a/b/c").getParent(), new Path("/a/b/"));
    Assert.assertEquals(new Path("/a/b/c").getParent().getParent(), new Path("/a/"));
    Assert.assertEquals(new Path("/a/b/c").getParent().getParent().getParent(), new Path("/"));
    Assert.assertNull(new Path("/a/b/c").getParent().getParent().getParent().getParent());
  }

  @Test
  public void wireFormat() {
    Assert.assertEquals("/", Path.getEmptyPath().wireFormat());
    Assert.assertEquals("a/b/c", new Path("/a/b//c/").wireFormat());
    Assert.assertEquals("b/c", new Path("/a/b//c/").popFront().wireFormat());
  }
}
