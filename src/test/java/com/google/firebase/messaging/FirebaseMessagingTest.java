package com.google.firebase.messaging;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestResponseInterceptor;
import org.junit.After;
import org.junit.Test;

public class FirebaseMessagingTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp.initializeApp(options);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    assertSame(messaging, FirebaseMessaging.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");

    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
    assertSame(messaging, FirebaseMessaging.getInstance(app));
  }

  @Test
  public void testPostDeleteApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId("test-project")
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "custom-app");
    app.delete();
    try {
      FirebaseMessaging.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testNoProjectId() throws FirebaseMessagingException {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();
    try {
      messaging.send(Message.builder()
          .setTopic("test-topic")
          .build());
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

//  @Test
//  public void testSendNullMessage() throws FirebaseMessagingException {
//    TestResponseInterceptor interceptor = new TestResponseInterceptor();
//    FirebaseMessagingClient messaging = initDefaultMessaging(interceptor);
//    try {
//      messaging.send(null, false);
//      fail("No error thrown for null message");
//    } catch (NullPointerException expected) {
//      // expected
//    }
//
//    assertNull(interceptor.getResponse());
//  }
//
//  @Test
//  public void testSendAllWithNull() throws  FirebaseMessagingException {
//    FirebaseMessagingClient messaging = initDefaultMessaging();
//    try {
//      messaging.sendAll(null, false);
//      fail("No error thrown for null message list");
//    } catch (NullPointerException expected) {
//      // expected
//    }
//  }
//
//  @Test
//  public void testSendAllWithEmptyList() throws FirebaseMessagingException {
//    FirebaseMessagingClient messaging = initDefaultMessaging();
//    try {
//      messaging.sendAll(ImmutableList.<Message>of(), false);
//      fail("No error thrown for empty message list");
//    } catch (IllegalArgumentException expected) {
//      // expected
//    }
//  }
//
//  @Test
//  public void testSendAllWithTooManyMessages() throws FirebaseMessagingException {
//    FirebaseMessagingClient messaging = initDefaultMessaging();
//    ImmutableList.Builder<Message> listBuilder = ImmutableList.builder();
//    for (int i = 0; i < 101; i++) {
//      listBuilder.add(Message.builder().setTopic("topic").build());
//    }
//    try {
//      messaging.sendAll(listBuilder.build(), false);
//      fail("No error thrown for too many messages in the list");
//    } catch (IllegalArgumentException expected) {
//      // expected
//    }
//  }

}
