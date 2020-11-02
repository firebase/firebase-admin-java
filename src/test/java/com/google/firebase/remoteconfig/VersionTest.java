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
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VersionTest {

  @Test
  public void testConstructor() {
    final Version version = new Version();

    assertNull(version.getVersionNumber());
    assertEquals(0, version.getUpdateTime());
    assertNull(version.getUpdateOrigin());
    assertNull(version.getUpdateType());
    assertNull(version.getUpdateUser());
    assertNull(version.getDescription());
    assertNull(version.getRollbackSource());
    assertFalse(version.isLegacy());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullVersionResponse() {
    new Version(null);
  }

  @Test
  public void testWithDescription() {
    final Version version = Version.withDescription("version description text");

    assertEquals("version description text", version.getDescription());
    assertNull(version.getVersionNumber());
    assertEquals(0, version.getUpdateTime());
    assertNull(version.getUpdateOrigin());
    assertNull(version.getUpdateType());
    assertNull(version.getUpdateUser());
    assertNull(version.getRollbackSource());
    assertFalse(version.isLegacy());
  }
}
