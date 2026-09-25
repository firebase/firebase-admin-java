/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.appcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class VerifyAppCheckTokenResponseTest {

  private static final String APP_ID = "test-app-id";
  private static final DecodedAppCheckToken DECODED_TOKEN =
      new DecodedAppCheckToken(ImmutableMap.<String, Object>of("sub", APP_ID));

  @Test
  public void testNullAppId_ThrowsException() {
    NullPointerException e =
        assertThrows(
            NullPointerException.class,
            () -> new VerifyAppCheckTokenResponse(null, DECODED_TOKEN, null));
    assertTrue(e.getMessage().contains("appId must not be null"));
  }

  @Test
  public void testNullToken_ThrowsException() {
    NullPointerException e =
        assertThrows(
            NullPointerException.class,
            () -> new VerifyAppCheckTokenResponse(APP_ID, null, null));
    assertTrue(e.getMessage().contains("token must not be null"));
  }

  @Test
  public void testGetters_WithoutAlreadyConsumed() {
    VerifyAppCheckTokenResponse response =
        new VerifyAppCheckTokenResponse(APP_ID, DECODED_TOKEN, null);

    assertNotNull(response);
    assertEquals(APP_ID, response.getAppId());
    assertEquals(DECODED_TOKEN, response.getToken());
    assertFalse(response.isAlreadyConsumed().isPresent());
  }

  @Test
  public void testGetters_WithAlreadyConsumedTrue() {
    VerifyAppCheckTokenResponse response =
        new VerifyAppCheckTokenResponse(APP_ID, DECODED_TOKEN, true);

    assertNotNull(response);
    assertEquals(APP_ID, response.getAppId());
    assertEquals(DECODED_TOKEN, response.getToken());
    assertTrue(response.isAlreadyConsumed().isPresent());
    assertTrue(response.isAlreadyConsumed().get());
  }

  @Test
  public void testGetters_WithAlreadyConsumedFalse() {
    VerifyAppCheckTokenResponse response =
        new VerifyAppCheckTokenResponse(APP_ID, DECODED_TOKEN, false);

    assertNotNull(response);
    assertEquals(APP_ID, response.getAppId());
    assertEquals(DECODED_TOKEN, response.getToken());
    assertTrue(response.isAlreadyConsumed().isPresent());
    assertFalse(response.isAlreadyConsumed().get());
  }
}
