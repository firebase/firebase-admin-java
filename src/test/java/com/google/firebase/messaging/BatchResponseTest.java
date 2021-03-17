/*
 * Copyright  2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class BatchResponseTest {

  @Test
  public void testEmptyResponses() {
    List<SendResponse> responses = new ArrayList<>();

    BatchResponse batchResponse = new BatchResponseImpl(responses);

    assertEquals(0, batchResponse.getSuccessCount());
    assertEquals(0, batchResponse.getFailureCount());
    assertEquals(0, batchResponse.getResponses().size());
  }

  @Test
  public void testSomeResponse() {
    ImmutableList<SendResponse> responses = ImmutableList.of(
        SendResponse.fromMessageId("message1"),
        SendResponse.fromMessageId("message2"),
        SendResponse.fromException(
            new FirebaseMessagingException(ErrorCode.INTERNAL, "error-message"))
    );

    BatchResponse batchResponse = new BatchResponseImpl(responses);

    assertEquals(2, batchResponse.getSuccessCount());
    assertEquals(1, batchResponse.getFailureCount());
    assertEquals(3, batchResponse.getResponses().size());
    for (int i = 0; i < 3; i ++) {
      assertSame(responses.get(i), batchResponse.getResponses().get(i));
    }
  }

  @Test
  public void testResponsesImmutable() {
    List<SendResponse> responses = new ArrayList<>();
    responses.add(SendResponse.fromMessageId("message1"));
    BatchResponse batchResponse = new BatchResponseImpl(responses);
    SendResponse sendResponse = SendResponse.fromMessageId("message2");

    try {
      batchResponse.getResponses().add(sendResponse);
      fail("No error thrown when modifying responses list");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  @Test(expected = NullPointerException.class)
  public void testResponsesCannotBeNull() {
    new BatchResponseImpl(null);
  }
}
