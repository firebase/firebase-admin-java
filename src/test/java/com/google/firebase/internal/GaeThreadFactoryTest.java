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

package com.google.firebase.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Assume;
import org.junit.Test;

public class GaeThreadFactoryTest {

  @Test
  public void testGaeThreadFactory() {
    Assume.assumeFalse(GaeThreadFactory.isAvailable());
    GaeThreadFactory threadFactory = GaeThreadFactory.getInstance();
    assertNotNull(threadFactory);
    assertFalse(threadFactory.isUsingBackgroundThreads());
    Thread thread = threadFactory.newThread(new Runnable() {
      @Override
      public void run() {
      }
    });
    assertNull(thread);
  }

}
