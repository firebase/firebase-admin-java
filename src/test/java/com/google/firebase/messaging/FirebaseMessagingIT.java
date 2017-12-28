package com.google.firebase.messaging;

import com.google.firebase.testing.IntegrationTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseMessagingIT {

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
            .setRestrictedPackageName("com.demoapps.hkj")
            .build())
        .setWebpushConfig(WebpushConfig.builder().putHeader("X-Custom-Val", "Foo").build())
        .setTopic("foo-bar")
        .build();
    String resp = messaging.sendAsync(message).get();
    System.out.println(resp);
  }
}
