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

import java.util.Optional;
import org.junit.Test;

public class VerifyAppCheckTokenOptionsTest {

  @Test
  public void testDefaultBuilder_EmptyConsume() {
    VerifyAppCheckTokenOptions options = VerifyAppCheckTokenOptions.builder().build();
    assertNotNull(options);
    assertFalse(options.getConsume().isPresent());
  }

  @Test
  public void testBuilder_SetConsumeTrue() {
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(true).build();
    assertNotNull(options);
    assertTrue(options.getConsume().isPresent());
    assertTrue(options.getConsume().get());
  }

  @Test
  public void testBuilder_SetConsumeFalse() {
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(false).build();
    assertNotNull(options);
    assertTrue(options.getConsume().isPresent());
    assertFalse(options.getConsume().get());
  }

  @Test
  public void testBuilder_SetConsumeOptional_Present() {
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(Optional.of(true)).build();
    assertNotNull(options);
    assertTrue(options.getConsume().isPresent());
    assertTrue(options.getConsume().get());
  }

  @Test
  public void testBuilder_SetConsumeOptional_Empty() {
    VerifyAppCheckTokenOptions options =
        VerifyAppCheckTokenOptions.builder().setConsume(Optional.empty()).build();
    assertNotNull(options);
    assertFalse(options.getConsume().isPresent());
  }

  @Test
  public void testBuilder_SetConsumeOptionalNull_ThrowsException() {
    NullPointerException e =
        assertThrows(
            NullPointerException.class,
            () -> VerifyAppCheckTokenOptions.builder().setConsume((Optional<Boolean>) null));
    assertEquals("consume must not be null", e.getMessage());
  }
}
