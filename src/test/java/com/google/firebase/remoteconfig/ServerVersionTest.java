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

public class ServerVersionTest {

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullServerVersionResponse() {
    new ServerVersion(null);
  }

  @Test
  public void testConstructorWithValidZuluUpdateTime() {
    ServerVersion serverVersion = new ServerVersion(new VersionResponse()
            .setUpdateTime("2020-12-08T15:49:51.887878Z"));
    assertEquals("2020-12-08T15:49:51.887878Z", serverVersion.getUpdateTime());
  }

  @Test
  public void testConstructorWithValidUTCUpdateTime() {
    ServerVersion serverVersion = new ServerVersion(new VersionResponse()
            .setUpdateTime("Tue, 08 Dec 2020 15:49:51 GMT"));
    assertEquals("Tue, 08 Dec 2020 15:49:51 GMT", serverVersion.getUpdateTime());
  }

  @Test
  public void testWithDescription() {
    final ServerVersion serverVersion = ServerVersion.withDescription("version description text");

    assertEquals("version description text", serverVersion.getDescription());
    assertNull(serverVersion.getVersionNumber());
    assertEquals(null, serverVersion.getUpdateTime());
    assertNull(serverVersion.getUpdateOrigin());
    assertNull(serverVersion.getUpdateType());
    assertNull(serverVersion.getUpdateUser());
    assertNull(serverVersion.getRollbackSource());
    assertFalse(serverVersion.isLegacy());
  }

  @Test
  public void testEquality() {
    final ServerVersion versionOne = new ServerVersion(new VersionResponse());
    final ServerVersion versionTwo = new ServerVersion(new VersionResponse());

    assertEquals(versionOne, versionTwo);

    final ServerVersion versionThree = ServerVersion.withDescription("abcd");
    final ServerVersion versionFour = ServerVersion.withDescription("abcd");
    final ServerVersion versionFive = new ServerVersion(new VersionResponse())
            .setDescription("abcd");

    assertEquals(versionThree, versionFour);
    assertEquals(versionThree, versionFive);

    final ServerVersion versionSix = ServerVersion.withDescription("efgh");

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
    final ServerVersion versionSeven = new ServerVersion(versionResponse);
    final ServerVersion versionEight = new ServerVersion(versionResponse);

    assertEquals(versionSeven, versionEight);
    assertNotEquals(versionOne, versionSeven);
    assertNotEquals(versionThree, versionSeven);
    assertNotEquals(versionSix, versionSeven);
  }
}
