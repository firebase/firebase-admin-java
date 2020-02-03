/*
 * Copyright 2019 Google Inc.
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

package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.ErrorCode;
import org.junit.Test;

public class SendResponseTest {

  @Test
  public void testSuccessfulResponse() {
    SendResponse response = SendResponse.fromMessageId("message-id");

    assertEquals("message-id", response.getMessageId());
    assertTrue(response.isSuccessful());
    assertNull(response.getException());
  }

  @Test
  public void testFailureResponse() {
    FirebaseMessagingException exception = new FirebaseMessagingException(
        ErrorCode.INTERNAL, "error-message");
    SendResponse response = SendResponse.fromException(exception);

    assertNull(response.getMessageId());
    assertFalse(response.isSuccessful());
    assertSame(exception, response.getException());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMessageIdCannotBeNull() {
    SendResponse.fromMessageId(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMessageIdCannotBeEmpty() {
    SendResponse.fromMessageId("");
  }

  @Test(expected = NullPointerException.class)
  public void testExceptionCannotBeNull() {
    SendResponse.fromException(null);
  }
}
