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
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class AppCheckErrorCodeTest {

  @Test
  public void testEnum() {
    assertNotNull(AppCheckErrorCode.valueOf("INVALID_ARGUMENT"));
    assertNotNull(AppCheckErrorCode.valueOf("INVALID_APP_CHECK_TOKEN"));
    assertNotNull(AppCheckErrorCode.valueOf("APP_CHECK_TOKEN_EXPIRED"));
    assertNotNull(AppCheckErrorCode.valueOf("INTERNAL_ERROR"));
    assertNotNull(AppCheckErrorCode.valueOf("SERVICE_ERROR"));
    assertEquals(5, AppCheckErrorCode.values().length);
  }
}
