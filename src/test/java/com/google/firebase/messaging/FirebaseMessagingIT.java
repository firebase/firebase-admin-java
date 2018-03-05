/*
 * Copyright 2018 Google Inc.
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
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.firebase.testing.IntegrationTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseMessagingIT {

  private static final String TEST_REGISTRATION_TOKEN =
      "fGw0qy4TGgk:APA91bGtWGjuhp4WRhHXgbabIYp1jxEKI08ofj_v1bKhWAGJQ4e3arRCWzeTfHaLz83mBnDh0a"
          + "PWB1AykXAVUUGl2h1wT4XI6XazWpvY7RBUSYfoxtqSWGIm2nvWh2BOP1YG501SsRoE";

  @BeforeClass
  public static void setUpClass() throws Exception {
    IntegrationTestUtils.ensureDefaultApp();
  }

  @Test
  public void testSend() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    Message message = Message.builder()
        .setNotification(new Notification("Title", "Body"))
        .setAndroidConfig(AndroidConfig.builder()
            .setRestrictedPackageName("com.google.firebase.testing")
            .build())
        .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("Title")
                    .setBody("Body")
                    .build())
                .build())
            .build())
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("X-Custom-Val", "Foo")
            .setNotification(new WebpushNotification("Title", "Body"))
            .build())
        .setTopic("foo-bar")
        .build();
    String id = messaging.sendAsync(message, true).get();
    assertTrue(id != null && id.matches("^projects/.*/messages/.*$"));
  }

  @Test
  public void testSubscribe() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TopicManagementResponse results = messaging.subscribeToTopicAsync(
        ImmutableList.of(TEST_REGISTRATION_TOKEN), "mock-topic").get();
    assertEquals(1, results.getSuccessCount() + results.getFailureCount());
  }

  @Test
  public void testUnsubscribe() throws Exception {
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    TopicManagementResponse results = messaging.unsubscribeFromTopicAsync(
        ImmutableList.of(TEST_REGISTRATION_TOKEN), "mock-topic").get();
    assertEquals(1, results.getSuccessCount() + results.getFailureCount());
  }
}
