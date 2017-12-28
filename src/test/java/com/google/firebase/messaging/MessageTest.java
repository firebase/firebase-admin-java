package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MessageTest {

  @Test(expected = IllegalArgumentException.class)
  public void testMessageWithoutTarget() throws IOException {
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
            .setPriority(AndroidConfig.Priority.high)
            .setTtl("10s")
            .setRestrictedPackageName("test-pkg-name")
            .putData("k1", "v1")
            .putAllData(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "collapse_key", "test-key",
        "priority", "high",
        "ttl", "10s",
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
            .setPriority(AndroidConfig.Priority.high)
            .setTtl("10.001s")
            .setRestrictedPackageName("test-pkg-name")
            .setNotification(AndroidNotification.builder()
                .setTitle("android-title")
                .setBody("android-body")
                .setIcon("android-icon")
                .setSound("android-sound")
                .setColor("#112233")
                .setTag("android-tag")
                .setClickAction("android-click")
                .setTitleLocKey("title-loc")
                .addTitleLocArg("title-arg1")
                .addAllTitleLocArgs(ImmutableList.of("title-arg2", "title-arg3"))
                .setBodyLocKey("body-loc")
                .addBodyLocArg("body-arg1")
                .addAllBodyLocArgs(ImmutableList.of("body-arg2", "body-arg3"))
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
        "ttl", "10.001s",
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
            .setPriority(AndroidConfig.Priority.high)
            .setTtl("10.001s")
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
        "priority", "high",
        "ttl", "10.001s",
        "restricted_package_name", "test-pkg-name",
        "notification", notification
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "android", data), message);
  }

  @Test
  public void testInvalidAndroidConfig() throws IOException {
    List<AndroidConfig.Builder> configBuilders = ImmutableList.of(
        AndroidConfig.builder().setTtl(""),
        AndroidConfig.builder().setTtl("s"),
        AndroidConfig.builder().setTtl("10"),
        AndroidConfig.builder().setTtl("10e1s"),
        AndroidConfig.builder().setTtl("1.2.3s"),
        AndroidConfig.builder().setTtl("10 s"),
        AndroidConfig.builder().setTtl("-10s")
    );
    for (int i = 0; i < configBuilders.size(); i++) {
      try {
        configBuilders.get(i).build();
        fail("No error thrown for invalid config: " + i);
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }

    List<AndroidNotification.Builder> notificationBuilders = ImmutableList.of(
        AndroidNotification.builder().setColor(""),
        AndroidNotification.builder().setColor("foo"),
        AndroidNotification.builder().setColor("123"),
        AndroidNotification.builder().setColor("#AABBCK"),
        AndroidNotification.builder().addBodyLocArg("foo"),
        AndroidNotification.builder().addTitleLocArg("foo")
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
        .setApnsConfig(ApnsConfig.builder().build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.of();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithoutPayload() throws IOException {
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);

    message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setPayload(null)
            .build())
        .setTopic("test-topic")
        .build();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);

    message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setPayload(ImmutableMap.<String, Object>of())
            .build())
        .setTopic("test-topic")
        .build();
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
  }

  @Test
  public void testApnsMessageWithPayload() throws IOException {
    Map<String, Object> payload = ImmutableMap.<String, Object>builder()
        .put("k1", "v1")
        .put("k2", true)
        .put("k3", ImmutableMap.of("k4", "v4"))
        .build();
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("k1", "v1")
            .putAllHeaders(ImmutableMap.of("k2", "v2", "k3", "v3"))
            .setPayload(payload)
            .build())
        .setTopic("test-topic")
        .build();
    Map<String, Object> data = ImmutableMap.<String, Object>of(
        "headers", ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"),
        "payload", payload
    );
    assertJsonEquals(ImmutableMap.of("topic", "test-topic", "apns", data), message);
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
