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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.remoteconfig.internal.TemplateResponse;
import com.google.firebase.remoteconfig.internal.TemplateResponse.VersionResponse;
import org.junit.Test;

public class VersionTest {

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullVersionResponse() {
    new Version(null);
  }

  @Test(expected = IllegalStateException.class)
  public void testConstructorWithInvalidUpdateTime() {
    new Version(new VersionResponse()
            .setUpdateTime("sunday,26th"));
  }

  @Test
  public void testConstructorWithValidZuluUpdateTime() {
    Version version = new Version(new VersionResponse()
            .setUpdateTime("2020-12-08T15:49:51.887878Z"));
    assertEquals(1607442591000L, version.getUpdateTime());
  }

  @Test
  public void testConstructorWithValidUTCUpdateTime() {
    Version version = new Version(new VersionResponse()
            .setUpdateTime("Tue, 08 Dec 2020 15:49:51 GMT"));
    assertEquals(1607442591000L, version.getUpdateTime());
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

  @Test
  public void testEquality() {
    final Version versionOne = new Version(new VersionResponse());
    final Version versionTwo = new Version(new VersionResponse());

    assertEquals(versionOne, versionTwo);

    final Version versionThree = Version.withDescription("abcd");
    final Version versionFour = Version.withDescription("abcd");
    final Version versionFive = new Version(new VersionResponse()).setDescription("abcd");

    assertEquals(versionThree, versionFour);
    assertEquals(versionThree, versionFive);

    final Version versionSix = Version.withDescription("efgh");

    assertNotEquals(versionThree, versionSix);
    assertNotEquals(versionOne, versionSix);

    final VersionResponse versionResponse = new VersionResponse()
            .setVersionNumber("23")
            .setUpdateTime("2014-10-02T15:01:23.045123456Z")
            .setUpdateOrigin("ADMIN_SDK")
            .setUpdateUser(new TemplateResponse.UserResponse()
                    .setEmail("user@email.com")
                    .setName("user-1234")
                    .setImageUrl("http://user.jpg"))
            .setUpdateType("INCREMENTAL_UPDATE");
    final Version versionSeven = new Version(versionResponse);
    final Version versionEight = new Version(versionResponse);

    assertEquals(versionSeven, versionEight);
    assertNotEquals(versionOne, versionSeven);
    assertNotEquals(versionThree, versionSeven);
    assertNotEquals(versionSix, versionSeven);
  }
}
