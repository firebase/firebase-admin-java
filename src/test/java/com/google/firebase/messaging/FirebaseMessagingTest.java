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
import static org.junit.Assert.fail;

import com.google.api.client.json.GenericJson;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import java.util.List;
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
  public void testSendAllWithNull() throws  FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendAll(null);
      fail("No error thrown for null message list");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(client.lastBatch);
  }

  @Test
  public void testSendAllWithEmptyList() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendAll(ImmutableList.<Message>of());
      fail("No error thrown for empty message list");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    assertNull(client.lastBatch);
  }

  @Test
  public void testSendAllWithTooManyMessages() throws FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList.Builder<Message> listBuilder = ImmutableList.builder();
    for (int i = 0; i < 501; i++) {
      listBuilder.add(Message.builder().setTopic("topic").build());
    }

    try {
      messaging.sendAll(listBuilder.build(), false);
      fail("No error thrown for too many messages in the list");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    assertNull(client.lastBatch);
  }

  @Test
  public void testSendAll() throws FirebaseMessagingException {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    BatchResponse response = messaging.sendAll(messages);

    assertSame(batchResponse, response);
    assertSame(messages, client.lastBatch);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendAllDryRun() throws FirebaseMessagingException {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    BatchResponse response = messaging.sendAll(messages, true);

    assertSame(batchResponse, response);
    assertSame(messages, client.lastBatch);
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendAllFailure() {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    try {
      messaging.sendAll(messages);
    } catch (FirebaseMessagingException e) {
      assertSame(TEST_EXCEPTION, e);
    }

    assertSame(messages, client.lastBatch);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendAllAsync() throws Exception {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    BatchResponse response = messaging.sendAllAsync(messages).get();

    assertSame(batchResponse, response);
    assertSame(messages, client.lastBatch);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendAllAsyncDryRun() throws Exception {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    BatchResponse response = messaging.sendAllAsync(messages, true).get();

    assertSame(batchResponse, response);
    assertSame(messages, client.lastBatch);
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendAllAsyncFailure() throws InterruptedException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));
    ImmutableList<Message> messages = ImmutableList.of(EMPTY_MESSAGE);

    try {
      messaging.sendAllAsync(messages).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }

    assertSame(messages, client.lastBatch);
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastWithNull() throws  FirebaseMessagingException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromMessageId(null);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendMulticast(null);
      fail("No error thrown for null multicast message");
    } catch (NullPointerException expected) {
      // expected
    }

    assertNull(client.lastBatch);
  }

  @Test
  public void testSendMulticast() throws FirebaseMessagingException {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendMulticast(TEST_MULTICAST_MESSAGE);

    assertSame(batchResponse, response);
    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastDryRun() throws FirebaseMessagingException {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendMulticast(TEST_MULTICAST_MESSAGE, true);

    assertSame(batchResponse, response);
    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastFailure() {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendMulticast(TEST_MULTICAST_MESSAGE);
    } catch (FirebaseMessagingException e) {
      assertSame(TEST_EXCEPTION, e);
    }

    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastAsync() throws Exception {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendMulticastAsync(TEST_MULTICAST_MESSAGE).get();

    assertSame(batchResponse, response);
    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
    assertFalse(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastAsyncDryRun() throws Exception {
    BatchResponse batchResponse = getBatchResponse("test");
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient
        .fromBatchResponse(batchResponse);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    BatchResponse response = messaging.sendMulticastAsync(TEST_MULTICAST_MESSAGE, true).get();

    assertSame(batchResponse, response);
    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
    assertTrue(client.isLastDryRun);
  }

  @Test
  public void testSendMulticastAsyncFailure() throws InterruptedException {
    MockFirebaseMessagingClient client = MockFirebaseMessagingClient.fromException(TEST_EXCEPTION);
    FirebaseMessaging messaging = getMessagingForSend(Suppliers.ofInstance(client));

    try {
      messaging.sendMulticastAsync(TEST_MULTICAST_MESSAGE).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }

    assertEquals(2, client.lastBatch.size());
    assertEquals("test-fcm-token1", client.lastBatch.get(0).getToken());
    assertEquals("test-fcm-token2", client.lastBatch.get(1).getToken());
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

  private BatchResponse getBatchResponse(String ...messageIds) {
    ImmutableList.Builder<SendResponse> listBuilder = ImmutableList.builder();
    for (String messageId : messageIds) {
      listBuilder.add(SendResponse.fromMessageId(messageId));
    }
    return new BatchResponseImpl(listBuilder.build());
  }

  private static class MockFirebaseMessagingClient implements FirebaseMessagingClient {

    private String messageId;
    private BatchResponse batchResponse;
    private FirebaseMessagingException exception;

    private Message lastMessage;
    private List<Message> lastBatch;
    private boolean isLastDryRun;

    private MockFirebaseMessagingClient(
        String messageId, BatchResponse batchResponse, FirebaseMessagingException exception) {
      this.messageId = messageId;
      this.batchResponse = batchResponse;
      this.exception = exception;
    }

    static MockFirebaseMessagingClient fromMessageId(String messageId) {
      return new MockFirebaseMessagingClient(messageId, null, null);
    }

    static MockFirebaseMessagingClient fromBatchResponse(BatchResponse batchResponse) {
      return new MockFirebaseMessagingClient(null, batchResponse, null);
    }

    static MockFirebaseMessagingClient fromException(FirebaseMessagingException exception) {
      return new MockFirebaseMessagingClient(null, null, exception);
    }

    @Override
    public String send(Message message, boolean dryRun) throws FirebaseMessagingException {
      lastMessage = message;
      isLastDryRun = dryRun;
      if (exception != null) {
        throw exception;
      }
      return messageId;
    }

    @Override
    public BatchResponse sendAll(
        List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
      lastBatch = messages;
      isLastDryRun = dryRun;
      if (exception != null) {
        throw exception;
      }
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
