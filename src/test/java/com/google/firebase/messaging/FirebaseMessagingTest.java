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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.json.GenericJson;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Test;

public class FirebaseMessagingTest {

  private static final FirebaseOptions TEST_OPTIONS = FirebaseOptions.builder()
      .setCredentials(new MockGoogleCredentials("test-token"))
      .setProjectId("test-project")
      .build();
  private static final Message EMPTY_MESSAGE = Message.builder()
      .setTopic("test-topic")
      .build();
  private static final Message EMPTY_MESSAGE_2 = Message.builder()
      .setTopic("test-topic2")
      .build();
  private static final MulticastMessage TEST_MULTICAST_MESSAGE = MulticastMessage.builder()
      .addToken("test-fcm-token1")
      .addToken("test-fcm-token2")
      .build();
  private static final FirebaseMessagingException TEST_EXCEPTION =
      new FirebaseMessagingException(ErrorCode.INTERNAL, "Test error message");

  private static final ImmutableList.Builder<String> TOO_MANY_IDS = ImmutableList.builder();

  static {
    for (int i = 0; i < 1001; i++) {
      TOO_MANY_IDS.add("id" + i);
    }
  }

  private static final List<TopicMgtArgs> INVALID_TOPIC_MGT_ARGS = ImmutableList.of(
      new TopicMgtArgs(null, null),
      new TopicMgtArgs(null, "test-topic"),
      new TopicMgtArgs(ImmutableList.<String>of(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), "test-topic"),
      new TopicMgtArgs(TOO_MANY_IDS.build(), "test-topic"),
      new TopicMgtArgs(ImmutableList.of(""), null),
      new TopicMgtArgs(ImmutableList.of("id"), ""),
      new TopicMgtArgs(ImmutableList.of("id"), "foo*")
  );
  private static final TopicManagementResponse TOPIC_MGT_RESPONSE = new TopicManagementResponse(
      ImmutableList.of(new GenericJson()));

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseApp.initializeApp(TEST_OPTIONS);

    FirebaseMessaging messaging = FirebaseMessaging.getInstance();

    assertSame(messaging, FirebaseMessaging.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");

    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

    assertSame(messaging, FirebaseMessaging.getInstance(app));
  }

  @Test
  public void testDefaultMessagingClient() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

    FirebaseMessagingClient client = messaging.getMessagingClient();

    assertTrue(client instanceof FirebaseMessagingClientImpl);
    assertSame(client, messaging.getMessagingClient());
    String expectedUrl = "https://fcm.googleapis.com/v1/projects/test-project/messages:send";
    assertEquals(expectedUrl, ((FirebaseMessagingClientImpl) client).getFcmSendUrl());
  }

  @Test
  public void testDefaultInstanceIdClient() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

    InstanceIdClient client = messaging.getInstanceIdClient();

    assertTrue(client instanceof InstanceIdClientImpl);
    assertSame(client, messaging.getInstanceIdClient());
  }

  @Test
  public void testPostDeleteApp() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");

    app.delete();

    try {
      FirebaseMessaging.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testMessagingClientWithoutProjectId() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();

    try {
      messaging.getMessagingClient();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      String message = "Project ID is required to access messaging service. Use a service "
          + "account credential or set the project ID explicitly via FirebaseOptions. "
          + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
          + "environment variable.";
      assertEquals(message, expected.getMessage());
    }
  }

  @Test
  public void testInstanceIdClientWithoutProjectId() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();

    InstanceIdClient client = messaging.getInstanceIdClient();

    assertTrue(client instanceof InstanceIdClientImpl);
    assertSame(client, messaging.getInstanceIdClient());
  }

  @Test
  public void testSendNullMessage() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.send(null);
      fail("No error thrown for null message");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(client.lastMessage);
  }

  @Test
  public void testSend() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    String messageId = messaging.send(EMPTY_MESSAGE);

    assertEquals("test", messageId);
    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendDryRun() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    String messageId = messaging.send(EMPTY_MESSAGE, true);

    assertEquals("test", messageId);
    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendFailure() {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.send(EMPTY_MESSAGE);
    } catch (FirebaseMessagingException e) {
      assertSame(TEST_EXCEPTION, e);
    }

    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendAsync() throws Exception {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    String messageId = messaging.sendAsync(EMPTY_MESSAGE).get();

    assertEquals("test", messageId);
    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendAsyncDryRun() throws Exception {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    String messageId = messaging.sendAsync(EMPTY_MESSAGE, true).get();

    assertEquals("test", messageId);
    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendAsyncFailure() throws InterruptedException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendAsync(EMPTY_MESSAGE).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }

    assertSame(EMPTY_MESSAGE, client.lastMessage);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachWithNull() throws  FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendEach(null);
      fail("No error thrown for null message list");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(client.lastMessage);
  }

  @Test
  public void testSendEachWithEmptyList() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendEach(ImmutableList.<Message>of());
      fail("No error thrown for empty message list");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    assertNull(client.lastMessage);
  }

  @Test
  public void testSendEachWithTooManyMessages() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList.Builder<Message> listBuilder = ImmutableList.builder();
    for (int i = 0; i < 501; i++) {
      listBuilder.add(Message.builder().setTopic("topic").build());
    }

    try {
      messaging.sendEach(listBuilder.build(), false);
      fail("No error thrown for too many messages in the list");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    assertNull(client.lastMessage);
  }

  @Test
  public void testSendEach() throws FirebaseMessagingException {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    ImmutableList<String> messageIds = ImmutableList.of("test1", "test2");
    Map<Message, SendResponse> messageMap = new HashMap<>();
    for (int i = 0; i < 2; i++) {
      messageMap.put(messages.get(i), SendResponse.fromMessageId(messageIds.get(i)));
    }
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEach(messages);

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(messageIds.get(i), response.getResponses().get(i).getMessageId());
    }
    assertThat(client.lastMessage, anyOf(is(EMPTY_MESSAGE), is(EMPTY_MESSAGE_2)));
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachDryRun() throws FirebaseMessagingException {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    ImmutableList<String> messageIds = ImmutableList.of("test1", "test2");
    Map<Message, SendResponse> messageMap = new HashMap<>();
    for (int i = 0; i < 2; i++) {
      messageMap.put(messages.get(i), SendResponse.fromMessageId(messageIds.get(i)));
    }
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEach(messages, true);

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(messageIds.get(i), response.getResponses().get(i).getMessageId());
    }
    assertThat(client.lastMessage, anyOf(is(EMPTY_MESSAGE), is(EMPTY_MESSAGE_2)));
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendEachFailure() throws FirebaseMessagingException {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    Map<Message, SendResponse> messageMap = new HashMap<>();
    messageMap.put(messages.get(0), SendResponse.fromMessageId("test"));
    messageMap.put(messages.get(1), SendResponse.fromException(TEST_EXCEPTION));
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response =  messaging.sendEach(messages);

    assertEquals(1, response.getFailureCount());
    assertEquals(1, response.getSuccessCount());
    assertEquals("test", response.getResponses().get(0).getMessageId());
    assertEquals(TEST_EXCEPTION, response.getResponses().get(1).getException());
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachAsync() throws Exception {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    ImmutableList<String> messageIds = ImmutableList.of("test1", "test2");
    Map<Message, SendResponse> messageMap = new HashMap<>();
    for (int i = 0; i < 2; i++) {
      messageMap.put(messages.get(i), SendResponse.fromMessageId(messageIds.get(i)));
    }
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachAsync(messages).get();

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(messageIds.get(i), response.getResponses().get(i).getMessageId());
    }
    assertThat(client.lastMessage, anyOf(is(EMPTY_MESSAGE), is(EMPTY_MESSAGE_2)));
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachAsyncDryRun() throws Exception {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    ImmutableList<String> messageIds = ImmutableList.of("test1", "test2");
    Map<Message, SendResponse> messageMap = new HashMap<>();
    for (int i = 0; i < 2; i++) {
      messageMap.put(messages.get(i), SendResponse.fromMessageId(messageIds.get(i)));
    }
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachAsync(messages, true).get();

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(messageIds.get(i), response.getResponses().get(i).getMessageId());
    }
    assertThat(client.lastMessage, anyOf(is(EMPTY_MESSAGE), is(EMPTY_MESSAGE_2)));
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendEachAsyncFailure() throws Exception {
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE, EMPTY_MESSAGE_2);
    Map<Message, SendResponse> messageMap = new HashMap<>();
    messageMap.put(messages.get(0), SendResponse.fromMessageId("test"));
    messageMap.put(messages.get(1), SendResponse.fromException(TEST_EXCEPTION));
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageMap(messageMap);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response =  messaging.sendEachAsync(messages).get();

    assertEquals(1, response.getFailureCount());
    assertEquals(1, response.getSuccessCount());
    assertEquals("test", response.getResponses().get(0).getMessageId());
    assertEquals(TEST_EXCEPTION, response.getResponses().get(1).getException());
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastWithNull() throws  FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendEachForMulticast(null);
      fail("No error thrown for null multicast message");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(client.lastMessage);
  }

  @Test
  public void testSendEachForMulticast() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachForMulticast(TEST_MULTICAST_MESSAGE);

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals("test", response.getResponses().get(i).getMessageId());
    }
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastDryRun() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachForMulticast(TEST_MULTICAST_MESSAGE, true);

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals("test", response.getResponses().get(i).getMessageId());
    }
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastFailure() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response =  messaging.sendEachForMulticast(TEST_MULTICAST_MESSAGE);

    assertEquals(2, response.getFailureCount());
    assertEquals(0, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(TEST_EXCEPTION, response.getResponses().get(i).getException());
    }
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastAsync() throws Exception {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachForMulticastAsync(TEST_MULTICAST_MESSAGE).get();

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals("test", response.getResponses().get(i).getMessageId());
    }
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastAsyncDryRun() throws Exception {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId("test");
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendEachForMulticastAsync(
        TEST_MULTICAST_MESSAGE, true).get();

    assertEquals(2, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals("test", response.getResponses().get(i).getMessageId());
    }
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendEachForMulticastAsyncFailure() throws Exception {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response =  messaging.sendEachForMulticastAsync(TEST_MULTICAST_MESSAGE).get();

    assertEquals(2, response.getFailureCount());
    assertEquals(0, response.getSuccessCount());
    for (int i = 0; i < 2; i++) {
      assertEquals(TEST_EXCEPTION, response.getResponses().get(i).getException());
    }
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testInvalidSubscribe() throws FirebaseMessagingException {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(null);
    FirebaseMessaging messaging = getMessagingForTopicManagement(
        Suppliers.<InstanceIdClient>ofInstance(client));

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.subscribeToTopic(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
      assertNull(client.lastTopic);
      assertNull(client.lastBatch);
    }
  }

  @Test
  public void testSubscribeToTopic() throws FirebaseMessagingException {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(TOPIC_MGT_RESPONSE);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    TopicManagementResponse got = messaging.subscribeToTopic(
        ImmutableList.of("id1", "id2"), "test-topic");

    assertSame(TOPIC_MGT_RESPONSE, got);
  }

  @Test
  public void testSubscribeToTopicFailure() {
    MockInstanceIdClient client = MockInstanceIdClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    try {
      messaging.subscribeToTopic(ImmutableList.of("id1", "id2"), "test-topic");
    } catch (FirebaseMessagingException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testSubscribeToTopicAsync() throws Exception {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(TOPIC_MGT_RESPONSE);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    TopicManagementResponse got = messaging.subscribeToTopicAsync(
        ImmutableList.of("id1", "id2"), "test-topic").get();

    assertSame(TOPIC_MGT_RESPONSE, got);
  }

  @Test
  public void testSubscribeToTopicAsyncFailure() throws InterruptedException {
    MockInstanceIdClient client = MockInstanceIdClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    try {
      messaging.subscribeToTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testInvalidUnsubscribe() throws FirebaseMessagingException {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(null);
    FirebaseMessaging messaging = getMessagingForTopicManagement(
        Suppliers.<InstanceIdClient>ofInstance(client));

    for (TopicMgtArgs args : INVALID_TOPIC_MGT_ARGS) {
      try {
        messaging.unsubscribeFromTopic(args.registrationTokens, args.topic);
        fail("No error thrown for invalid args");
      } catch (IllegalArgumentException expected) {
        // expected
      }
      assertNull(client.lastTopic);
      assertNull(client.lastBatch);
    }
  }

  @Test
  public void testUnsubscribeFromTopic() throws FirebaseMessagingException {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(TOPIC_MGT_RESPONSE);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    TopicManagementResponse got = messaging.unsubscribeFromTopic(
        ImmutableList.of("id1", "id2"), "test-topic");

    assertSame(TOPIC_MGT_RESPONSE, got);
  }

  @Test
  public void testUnsubscribeFromTopicFailure() {
    MockInstanceIdClient client = MockInstanceIdClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    try {
      messaging.unsubscribeFromTopic(ImmutableList.of("id1", "id2"), "test-topic");
    } catch (FirebaseMessagingException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testUnsubscribeFromTopicAsync() throws Exception {
    MockInstanceIdClient client = MockInstanceIdClient.fromResponse(TOPIC_MGT_RESPONSE);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    TopicManagementResponse got = messaging.unsubscribeFromTopicAsync(
        ImmutableList.of("id1", "id2"), "test-topic").get();

    assertSame(TOPIC_MGT_RESPONSE, got);
  }

  @Test
  public void testUnsubscribeFromTopicAsyncFailure() throws InterruptedException {
    MockInstanceIdClient client = MockInstanceIdClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForTopicManagement(Suppliers.ofInstance(client));

    try {
      messaging.unsubscribeFromTopicAsync(ImmutableList.of("id1", "id2"), "test-topic").get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  private FirebaseMessaging getMessagingForSend(
      Supplier<? extends FirebaseMessagingClient> supplier) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);
    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(supplier)
        .setInstanceIdClient(Suppliers.<InstanceIdClient>ofInstance(null))
        .build();
  }

  private FirebaseMessaging getMessagingForTopicManagement(
      Supplier<? extends InstanceIdClient> supplier) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);
    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(Suppliers.<FirebaseMessagingClient>ofInstance(null))
        .setInstanceIdClient(supplier)
        .build();
  }

  private static class MockFirebaseMessagingClient implements FirebaseMessagingClient {

    private String messageId;
    private BatchResponse batchResponse;
    private FirebaseMessagingException exception;

    private Message lastMessage;
    private boolean isLastDryRun;
    private ImmutableMap<Message, SendResponse> messageMap;

    private MockFirebaseMessagingClient(
        String messageId, BatchResponse batchResponse, FirebaseMessagingException exception) {
      this.messageId = messageId;
      this.batchResponse = batchResponse;
      this.exception = exception;
    }

    private MockFirebaseMessagingClient(
        Map<Message, SendResponse> messageMap, FirebaseMessagingException exception) {
      this.messageMap = ImmutableMap.copyOf(messageMap);
      this.exception = exception;
    }

    static MockFirebaseMessagingClient fromMessageId(String messageId) {
      return new MockFirebaseMessagingClient(messageId, null, null);
    }

    static MockFirebaseMessagingClient fromMessageMap(Map<Message, SendResponse> messageMap) {
      return new MockFirebaseMessagingClient(messageMap, null);
    }

    static MockFirebaseMessagingClient fromException(FirebaseMessagingException exception) {
      return new MockFirebaseMessagingClient(null, null, exception);
    }

    @Override
    @Nullable
    public String send(Message message, boolean dryRun) throws FirebaseMessagingException {
      lastMessage = message;
      isLastDryRun = dryRun;
      if (exception != null) {
        throw exception;
      }
      if (messageMap == null) {
        return messageId;
      }
      if (!messageMap.containsKey(message)) {
        return null;
      }
      if (messageMap.get(message).getException() != null) {
        throw messageMap.get(message).getException();
      }
      return messageMap.get(message).getMessageId();
    }

    @Override
    public BatchResponse sendAll(
        List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
      return batchResponse;
    }
  }

  private static class MockInstanceIdClient implements InstanceIdClient {

    private TopicManagementResponse response;
    private FirebaseMessagingException exception;

    private String lastTopic;
    private List<String> lastBatch;

    private MockInstanceIdClient(
        TopicManagementResponse response, FirebaseMessagingException exception) {
      this.response = response;
      this.exception = exception;
    }

    static MockInstanceIdClient fromResponse(TopicManagementResponse response) {
      return new MockInstanceIdClient(response, null);
    }

    static MockInstanceIdClient fromException(FirebaseMessagingException exception) {
      return new MockInstanceIdClient(null, exception);
    }

    @Override
    public TopicManagementResponse subscribeToTopic(
        String topic, List<String> registrationTokens) throws FirebaseMessagingException {
      this.lastTopic = topic;
      this.lastBatch = registrationTokens;
      if (exception != null) {
        throw exception;
      }
      return response;
    }

    @Override
    public TopicManagementResponse unsubscribeFromTopic(
        String topic, List<String> registrationTokens) throws FirebaseMessagingException {
      this.lastTopic = topic;
      this.lastBatch = registrationTokens;
      if (exception != null) {
        throw exception;
      }
      return response;
    }
  }

  private static class TopicMgtArgs {
    private final List<String> registrationTokens;
    private final String topic;

    TopicMgtArgs(List<String> registrationTokens, String topic) {
      this.registrationTokens = registrationTokens;
      this.topic = topic;
    }
  }
}
