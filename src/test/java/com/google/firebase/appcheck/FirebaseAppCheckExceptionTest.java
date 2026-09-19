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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import org.junit.Test;

public class FirebaseAppCheckExceptionTest {

  @Test
  public void testConstructorWithAppCheckErrorCode() {
    Exception cause = new Exception("cause");
    FirebaseAppCheckException exception =
        new FirebaseAppCheckException(
            ErrorCode.INVALID_ARGUMENT,
            "Invalid token",
            cause,
            null,
            AppCheckErrorCode.APP_CHECK_TOKEN_EXPIRED);

    assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
    assertEquals("Invalid token", exception.getMessage());
    assertSame(cause, exception.getCause());
    assertNull(exception.getHttpResponse());
    assertEquals(AppCheckErrorCode.APP_CHECK_TOKEN_EXPIRED, exception.getAppCheckErrorCode());
  }

  @Test
  public void testConstructorWithoutAppCheckErrorCode() {
    FirebaseAppCheckException exception =
        new FirebaseAppCheckException(ErrorCode.INTERNAL, "Internal error");

    assertEquals(ErrorCode.INTERNAL, exception.getErrorCode());
    assertEquals("Internal error", exception.getMessage());
    assertNull(exception.getCause());
    assertNull(exception.getHttpResponse());
    assertNull(exception.getAppCheckErrorCode());
  }

  @Test
  public void testConstructorWithCause() {
    Exception cause = new Exception("cause");
    FirebaseAppCheckException exception =
        new FirebaseAppCheckException(ErrorCode.UNKNOWN, "Unknown error", cause);

    assertEquals(ErrorCode.UNKNOWN, exception.getErrorCode());
    assertEquals("Unknown error", exception.getMessage());
    assertSame(cause, exception.getCause());
    assertNull(exception.getAppCheckErrorCode());
  }

  @Test
  public void testConstructorFromFirebaseException() {
    FirebaseException base =
        new FirebaseException(ErrorCode.UNAVAILABLE, "Service unavailable", null);
    FirebaseAppCheckException exception = new FirebaseAppCheckException(base);

    assertEquals(ErrorCode.UNAVAILABLE, exception.getErrorCode());
    assertEquals("Service unavailable", exception.getMessage());
    assertNull(exception.getAppCheckErrorCode());
  }
}
