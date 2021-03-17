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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;

public class MulticastMessageTest {

  private static final AndroidConfig ANDROID = AndroidConfig.builder()
      .setCollapseKey("collapseKey")
      .build();
  private static final ApnsConfig APNS = ApnsConfig.builder()
      .setAps(Aps.builder()
        .setBadge(42)
        .build())
      .build();
  private static final WebpushConfig WEBPUSH = WebpushConfig.builder()
      .putData("key", "value")
      .build();
  private static final Notification NOTIFICATION = Notification.builder()
      .setTitle("title")
      .setBody("body")
      .build();
  private static final FcmOptions FCM_OPTIONS = FcmOptions.withAnalyticsLabel("analytics_label");

  @Test
  public void testMulticastMessage() {
    MulticastMessage multicastMessage = MulticastMessage.builder()
        .setAndroidConfig(ANDROID)
        .setApnsConfig(APNS)
        .setWebpushConfig(WEBPUSH)
        .setNotification(NOTIFICATION)
        .setFcmOptions(FCM_OPTIONS)
        .putData("key1", "value1")
        .putAllData(ImmutableMap.of("key2", "value2"))
        .addToken("token1")
        .addAllTokens(ImmutableList.of("token2", "token3"))
        .build();

    List<Message> messages = multicastMessage.getMessageList();

    assertEquals(3, messages.size());
    for (int i = 0; i < 3; i++) {
      Message message = messages.get(i);
      assertMessage(message, "token" + (i + 1));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoTokens() {
    MulticastMessage.builder().build();
  }

  @Test
  public void testTooManyTokens() {
    MulticastMessage.Builder builder = MulticastMessage.builder();
    for (int i = 0; i < 501; i++) {
      builder.addToken("token" + i);
    }
    try {
      builder.build();
      fail("No error thrown for more than 500 tokens");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test(expected = NullPointerException.class)
  public void testNullToken() {
    MulticastMessage.builder().addToken(null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEmptyToken() {
    MulticastMessage.builder().addToken("").build();
  }

  private void assertMessage(Message message, String expectedToken) {
    assertSame(ANDROID, message.getAndroidConfig());
    assertSame(APNS, message.getApnsConfig());
    assertSame(WEBPUSH, message.getWebpushConfig());
    assertSame(NOTIFICATION, message.getNotification());
    assertSame(FCM_OPTIONS, message.getFcmOptions());
    assertEquals(ImmutableMap.of("key1", "value1", "key2", "value2"), message.getData());
    assertEquals(expectedToken, message.getToken());
  }
}
