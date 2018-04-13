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
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.messaging.AndroidConfig.Priority;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class MessageTest {

  @Test(expected = IllegalArgumentException.class)
  public void testMessageWithoutTarget() {
    Message.builder().build();
  }

  @Test
  public void testEmptyMessage() throws IOException {
    assertJsonEquals(ImmutableMap.of("topic", "test-topic"),
        Message.builder().setTopic("test-topic").build());
    assertJsonEquals(ImmutableMap.of("condition", "'foo' in topics"),
        Message.builder().setCondition("'foo' in topics").build());
    assertJsonEquals(ImmutableMap.of("token", "test-token"),
        Message.builder().setToken("test-token").build());
  }

  @Test
  public void testDataMessage() throws IOException {
    Message message = Message.builder()
        .putData("k1", "v1")
        .putData("k2", "v2")
        .putAllData(ImmutableMap.of("k3", "v3", "k4", "v4"))
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4");
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "data", data), message);
  }

  @Test
  public void testInvalidTopicNames() {
    List<String> invalidTopicNames = ImmutableList.of("/topics/", "/foo/bar", "foo bar");
    for (String topicName : invalidTopicNames) {
      try {
        Message.builder().setTopic(topicName).build();
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testPrefixedTopicName() throws IOException {
    Message message = Message.builder()
        .setTopic("/topics/test-topic")
        .build();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic"), message);
  }

  @Test
  public void testNotificationMessage() throws IOException {
    Message message = Message.builder()
        .setNotification(new Notification("title", "body"))
        .setTopic("test-topic")
        .build();
    Map<String, String> data = ImmutableMap.of("title", "title", "body", "body");
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "notification", data), message);
  }

  @Test
  public void testEmptyAndroidMessage() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder().build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.of();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithoutNotification() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.HIGH)
            .setTtl(10)
            .setRestrictedPackageName("test-pkg-name")
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "collapse_key", "test-key",
        "priority", "high",
        "ttl", "0.010000000s", // 10 ms = 10,000,000 ns
        "restricted_package_name", "test-pkg-name",
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithNotification() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.HIGH)
            .setTtl(TimeUnit.DAYS.toMillis(30))
            .setRestrictedPackageName("test-pkg-name")
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setIcon("android-icon")
                .setSound("android-sound")
                .setColor("#112233")
                .setTag("android-tag")
                .setClickAction("android-click")
                .setTitleLocalizationKey("title-loc")
                .addTitleLocalizationArg("title-arg1")
                .addAllTitleLocalizationArgs(ImmutableList.of("title-arg2", "title-arg3"))
                .setBodyLocalizationKey("body-loc")
                .addBodyLocalizationArg("body-arg1")
                .addAllBodyLocalizationArgs(ImmutableList.of("body-arg2", "body-arg3"))
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "android-title")
        .put("body", "android-body")
        .put("icon", "android-icon")
        .put("sound", "android-sound")
        .put("color", "#112233")
        .put("tag", "android-tag")
        .put("click_action", "android-click")
        .put("title_loc_key", "title-loc")
        .put("title_loc_args", ImmutableList.of("title-arg1", "title-arg2", "title-arg3"))
        .put("body_loc_key", "body-loc")
        .put("body_loc_args", ImmutableList.of("body-arg1", "body-arg2", "body-arg3"))
        .build();
    Map<String, Object> data = ImmutableMap.of(
        "collapse_key", "test-key",
        "priority", "high",
        "ttl", "2592000s",
        "restricted_package_name", "test-pkg-name",
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testAndroidMessageWithoutLocalization() throws IOException {
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setCollapseKey("test-key")
            .setPriority(Priority.NORMAL)
            .setTtl(TimeUnit.SECONDS.toMillis(10))
            .setRestrictedPackageName("test-pkg-name")
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setIcon("android-icon")
                .setSound("android-sound")
                .setColor("#112233")
                .setTag("android-tag")
                .setClickAction("android-click")
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "android-title")
        .put("body", "android-body")
        .put("icon", "android-icon")
        .put("sound", "android-sound")
        .put("color", "#112233")
        .put("tag", "android-tag")
        .put("click_action", "android-click")
        .build();
    Map<String, Object> data = ImmutableMap.of(
        "collapse_key", "test-key",
        "priority", "normal",
        "ttl", "10s",
        "restricted_package_name", "test-pkg-name",
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testInvalidAndroidConfig() {
    try {
      AndroidConfig.builder().setTtl(-1).build();
      fail("No error thrown for invalid ttl");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    List<AndroidNotification.Builder> notificationBuilders = ImmutableList.of(
        AndroidNotification.builder().setColor(""),
        AndroidNotification.builder().setColor("foo"),
        AndroidNotification.builder().setColor("123"),
        AndroidNotification.builder().setColor("#AABBCK"),
        AndroidNotification.builder().addBodyLocalizationArg("foo"),
        AndroidNotification.builder().addTitleLocalizationArg("foo")
    );
    for (int i = 0; i < notificationBuilders.size(); i++) {
      try {
        notificationBuilders.get(i).build();
        fail("No error thrown for invalid notification: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testEmptyWebpushMessage() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder().build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.of();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testWebpushMessageWithoutNotification() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testWebpushMessageWithNotification() throws IOException {
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setNotification(new WebpushNotification(
                "webpush-title", "webpush-body", "webpush-icon"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> notification = ImmutableMap.<String, Object>builder()
        .put("title", "webpush-title")
        .put("body", "webpush-body")
        .put("icon", "webpush-icon")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);

    // Test notification without icon
    message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setNotification(new WebpushNotification("webpush-title", "webpush-body"))
            .build())
        .setTopic("test-topic")
        .build();
    notification = ImmutableMap.<String, Object>builder()
        .put("title", "webpush-title")
        .put("body", "webpush-body")
        .build();
    data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "data", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "webpush", data), message);
  }

  @Test
  public void testEmptyApnsMessage() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().build()).build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of("payload",
        ImmutableMap.of("aps", ImmutableMap.of()));
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithPayload() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .putCustomData("cd1", "cd-v1")
            .putAllCustomData(ImmutableMap.<String, Object>of("cd2", "cd-v2", "cd3", true))
            .setAps(Aps.builder().build())
            .build())
        .setTopic("test-topic")
        .build();

    Map<String, Object> payload = ImmutableMap.<String, Object>builder()
        .put("cd1", "cd-v1")
        .put("cd2", "cd-v2")
        .put("cd3", true)
        .put("aps", ImmutableMap.of())
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "payload", payload
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithPayloadAndAps() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putCustomData("cd1", "cd-v1")
            .setAps(Aps.builder()
                .setAlert("alert string")
                .setBadge(42)
                .setCategory("test-category")
                .setContentAvailable(true)
                .setMutableContent(true)
                .setSound("test-sound")
                .setThreadId("test-thread-id")
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> payload = ImmutableMap.<String, Object>of(
        "cd1", "cd-v1",
        "aps", ImmutableMap.builder()
            .put("alert", "alert string")
            .put("badge", new BigDecimal(42))
            .put("category", "test-category")
            .put("content-available", new BigDecimal(1))
            .put("mutable-content", new BigDecimal(1))
            .put("sound", "test-sound")
            .put("thread-id", "test-thread-id")
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);

    message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putCustomData("cd1", "cd-v1")
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("test-title")
                    .setBody("test-body")
                    .setLocalizationKey("test-loc-key")
                    .setActionLocalizationKey("test-action-loc-key")
                    .setTitleLocalizationKey("test-title-loc-key")
                    .addLocalizationArg("arg1")
                    .addAllLocalizationArgs(ImmutableList.of("arg2", "arg3"))
                    .addTitleLocalizationArg("arg4")
                    .addAllTitleLocArgs(ImmutableList.of("arg5", "arg6"))
                    .setLaunchImage("test-image")
                    .build())
                .setCategory("test-category")
                .setSound("test-sound")
                .setThreadId("test-thread-id")
                .putCustomData("ck1", "cv1")
                .putAllCustomData(ImmutableMap.<String, Object>of("ck2", "cv2", "ck3", 1))
                .build())
            .build())
        .setTopic("test-topic")
        .build();
    payload = ImmutableMap.<String, Object>of(
        "cd1", "cd-v1",
        "aps", ImmutableMap.<String, Object>builder()
            .put("alert", ImmutableMap.<String, Object>builder()
                .put("title", "test-title")
                .put("body", "test-body")
                .put("loc-key", "test-loc-key")
                .put("action-loc-key", "test-action-loc-key")
                .put("title-loc-key", "test-title-loc-key")
                .put("loc-args", ImmutableList.of("arg1", "arg2", "arg3"))
                .put("title-loc-args", ImmutableList.of("arg4", "arg5", "arg6"))
                .put("launch-image", "test-image")
                .build())
            .put("category", "test-category")
            .put("sound", "test-sound")
            .put("thread-id", "test-thread-id")
            .put("ck1", "cv1")
            .put("ck2", "cv2")
            .put("ck3", new BigDecimal(1))
            .build());
    assertJsonEquals(
        ImmutableMap.of(
            "topic", "test-topic",
            "apns", ImmutableMap.<String, Object>of("payload", payload)),
        message);
  }

  @Test
  public void testInvalidApnsConfig() {
    List<ApnsConfig.Builder> configBuilders = ImmutableList.of(
        ApnsConfig.builder(),
        ApnsConfig.builder().putCustomData("aps", "foo"),
        ApnsConfig.builder().putCustomData("aps", "foo").setAps(Aps.builder().build())
    );
    for (int i = 0; i < configBuilders.size(); i++) {
      try {
        configBuilders.get(i).build();
        fail("No error thrown for invalid config: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    Aps.Builder builder = Aps.builder().setAlert("string").setAlert(ApsAlert.builder().build());
    try {
      builder.build();
      fail("No error thrown for invalid aps");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    builder = Aps.builder().setMutableContent(true).putCustomData("mutable-content", 1);
    try {
      builder.build();
      fail("No error thrown for invalid aps");
    } catch (IllegalArgumentException expected) {
      // expected
    }


    List<ApsAlert.Builder> notificationBuilders = ImmutableList.of(
        ApsAlert.builder().addLocalizationArg("foo"),
        ApsAlert.builder().addTitleLocalizationArg("foo")
    );
    for (int i = 0; i < notificationBuilders.size(); i++) {
      try {
        notificationBuilders.get(i).build();
        fail("No error thrown for invalid alert: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  private static void assertJsonEquals(
      Map expected, Object actual) throws IOException {
    assertEquals(expected, toMap(actual));
  }

  private static Map<String, Object> toMap(Object object) throws IOException {
    JsonFactory jsonFactory = Utils.getDefaultJsonFactory();
    String json = jsonFactory.toString(object);
    JsonParser parser = jsonFactory.createJsonParser(json);
    Map<String, Object> map = new HashMap<>();
    parser.parse(map);
    return map;
  }

}
