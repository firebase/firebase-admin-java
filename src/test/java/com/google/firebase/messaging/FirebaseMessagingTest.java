package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class FirebaseMessagingTest {

  private static final FirebaseOptions TEST_OPTIONS = new FirebaseOptions.Builder()
      .setCredentials(new MockGoogleCredentials("test-token"))
      .setProjectId("test-project")
      .build();
  private static final Message EMPTY_MESSAGE = Message.builder()
      .setTopic("test-topic")
      .build();
  private static final FirebaseMessagingException TEST_EXCEPTION =
      new FirebaseMessagingException("TEST_CODE", "Test error message", new Exception());

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
  public void testNoProjectId() throws FirebaseMessagingException {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseMessaging messaging = FirebaseMessaging.getInstance();

    try {
      messaging.send(EMPTY_MESSAGE);
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
    for (int i = 0; i < 101; i++) {
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

  private FirebaseMessaging getMessagingForSend(
      Supplier<? extends FirebaseMessagingClient> supplier) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);
    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(supplier)
        .setInstanceIdClient(Suppliers.<InstanceIdClient>ofInstance(null))
        .build();
  }

  private BatchResponse getBatchResponse(String messageId) {
    SendResponse response = SendResponse.fromMessageId(messageId);
    return new BatchResponse(ImmutableList.of(response));
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
}
