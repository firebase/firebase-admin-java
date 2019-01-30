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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.core.ApiFuture;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.util.List;

/**
 * This class is the entry point for all server-side Firebase Cloud Messaging actions.
 *
 * <p>You can get an instance of FirebaseMessaging via {@link #getInstance(FirebaseApp)}, and
 * then use it to send messages or manage FCM topic subscriptions.
 */
public class FirebaseMessaging {

  private final FirebaseApp app;
  private final FirebaseMessagingClient messagingClient;

  private FirebaseMessaging(FirebaseApp app) {
    this(app, null);
  }

  FirebaseMessaging(FirebaseApp app, @Nullable HttpResponseInterceptor responseInterceptor) {
    this.app = checkNotNull(app, "app must not be null");
    this.messagingClient = new FirebaseMessagingClient(app, responseInterceptor);
  }

  /**
   * Gets the {@link FirebaseMessaging} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebaseMessaging} instance for the default {@link FirebaseApp}.
   */
  public static FirebaseMessaging getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseMessaging} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebaseMessaging} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseMessaging getInstance(FirebaseApp app) {
    FirebaseMessagingService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseMessagingService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseMessagingService(app));
    }
    return service.getInstance();
  }

  /**
   * Sends the given {@link Message} via Firebase Cloud Messaging.
   *
   * @param message A non-null {@link Message} to be sent.
   * @return A message ID string.
   */
  public String send(@NonNull Message message) throws FirebaseMessagingException {
    return send(message, false);
  }

  /**
   * Sends the given {@link Message} via Firebase Cloud Messaging.
   *
   * <p>If the {@code dryRun} option is set to true, the message will not be actually sent. Instead
   * FCM performs all the necessary validations, and emulates the send operation.
   *
   * @param message A non-null {@link Message} to be sent.
   * @param dryRun a boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A message ID string.
   */
  public String send(@NonNull Message message, boolean dryRun) throws FirebaseMessagingException {
    return sendOp(message, dryRun).call();
  }

  /**
   * Similar to {@link #send(Message)} but performs the operation asynchronously.
   *
   * @param message A non-null {@link Message} to be sent.
   * @return An {@code ApiFuture} that will complete with a message ID string when the message
   *     has been sent.
   */
  public ApiFuture<String> sendAsync(@NonNull Message message) {
    return sendAsync(message, false);
  }

  /**
   * Similar to {@link #send(Message, boolean)} but performs the operation asynchronously.
   *
   * @param message A non-null {@link Message} to be sent.
   * @param dryRun a boolean indicating whether to perform a dry run (validation only) of the send.
   * @return An {@code ApiFuture} that will complete with a message ID string when the message
   *     has been sent, or when the emulation has finished.
   */
  public ApiFuture<String> sendAsync(@NonNull Message message, boolean dryRun) {
    return sendOp(message, dryRun).callAsync(app);
  }

  private CallableOperation<String, FirebaseMessagingException> sendOp(
      final Message message, final boolean dryRun) {
    checkNotNull(message, "message must not be null");
    return new CallableOperation<String, FirebaseMessagingException>() {
      @Override
      protected String execute() throws FirebaseMessagingException {
        return messagingClient.send(message, dryRun);
      }
    };
  }

  /**
   * Sends all the messages in the given list as a single batch via Firebase Cloud Messaging.
   *
   * @param messages A non-null, non-empty list of messages
   * @return A list of {@link BatchResponse} instances corresponding to the list of messages sent.
   */
  public List<BatchResponse> sendBatch(
      @NonNull List<Message> messages) throws FirebaseMessagingException {
    return sendBatch(messages, false);
  }

  public List<BatchResponse> sendBatch(
      BatchMessage batch, boolean dryRun) throws FirebaseMessagingException {
    checkNotNull(batch, "batch message must not be null");
    return sendBatch(batch.getMessageList(), dryRun);
  }

  public List<BatchResponse> sendBatch(BatchMessage batch) throws FirebaseMessagingException {
    return sendBatch(batch, false);
  }

  public List<BatchResponse> sendBatch(
      List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
    return sendBatchOp(messages, dryRun).call();
  }

  public ApiFuture<List<BatchResponse>> sendBatchAsync(BatchMessage batch) {
    return sendBatchAsync(batch, false);
  }

  public ApiFuture<List<BatchResponse>> sendBatchAsync(BatchMessage batch, boolean dryRun) {
    checkNotNull(batch, "batch message must not be null");
    return sendBatchAsync(batch.getMessageList(), dryRun);
  }

  public ApiFuture<List<BatchResponse>> sendBatchAsync(List<Message> messages) {
    return sendBatchAsync(messages, false);
  }

  public ApiFuture<List<BatchResponse>> sendBatchAsync(List<Message> messages, boolean dryRun) {
    return sendBatchOp(messages, dryRun).callAsync(app);
  }

  private CallableOperation<List<BatchResponse>, FirebaseMessagingException> sendBatchOp(
      final List<Message> messages, final boolean dryRun) {

    final List<Message> immutableMessages = ImmutableList.copyOf(messages);
    checkArgument(!immutableMessages.isEmpty(), "messages list must not be empty");
    checkArgument(immutableMessages.size() <= 1000,
        "messages list must not contain more than 1000 elements");
    return new CallableOperation<List<BatchResponse>,FirebaseMessagingException>() {
      @Override
      protected List<BatchResponse> execute() throws FirebaseMessagingException {
        return messagingClient.sendBatch(messages, dryRun);
      }
    };
  }

  /**
   * Subscribes a list of registration tokens to a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to subscribe to. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  public TopicManagementResponse subscribeToTopic(@NonNull List<String> registrationTokens,
      @NonNull String topic) throws FirebaseMessagingException {
    return subscribeOp(registrationTokens, topic).call();
  }

  /**
   * Similar to {@link #subscribeToTopic(List, String)} but performs the operation asynchronously.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to subscribe to. May contain the {@code /topics/} prefix.
   * @return An {@code ApiFuture} that will complete with a {@link TopicManagementResponse}.
   */
  public ApiFuture<TopicManagementResponse> subscribeToTopicAsync(
      @NonNull List<String> registrationTokens, @NonNull String topic) {
    return subscribeOp(registrationTokens, topic).callAsync(app);
  }

  private CallableOperation<TopicManagementResponse, FirebaseMessagingException> subscribeOp(
      final List<String> registrationTokens, final String topic) {
    checkRegistrationTokens(registrationTokens);
    checkTopic(topic);
    return new CallableOperation<TopicManagementResponse, FirebaseMessagingException>() {
      @Override
      protected TopicManagementResponse execute() throws FirebaseMessagingException {
        return messagingClient.subscribeToTopic(topic, registrationTokens);
      }
    };
  }

  /**
   * Unubscribes a list of registration tokens from a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to unsubscribe from. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  public TopicManagementResponse unsubscribeFromTopic(@NonNull List<String> registrationTokens,
      @NonNull String topic) throws FirebaseMessagingException {
    return unsubscribeOp(registrationTokens, topic).call();
  }

  /**
   * Similar to {@link #unsubscribeFromTopic(List, String)} but performs the operation
   * asynchronously.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens, with at
   *     most 1000 entries.
   * @param topic Name of the topic to unsubscribe from. May contain the {@code /topics/} prefix.
   * @return An {@code ApiFuture} that will complete with a {@link TopicManagementResponse}.
   */
  public ApiFuture<TopicManagementResponse> unsubscribeFromTopicAsync(
      @NonNull List<String> registrationTokens, @NonNull String topic) {
    return unsubscribeOp(registrationTokens, topic).callAsync(app);
  }

  private CallableOperation<TopicManagementResponse, FirebaseMessagingException> unsubscribeOp(
      final List<String> registrationTokens, final String topic) {
    checkRegistrationTokens(registrationTokens);
    checkTopic(topic);
    return new CallableOperation<TopicManagementResponse, FirebaseMessagingException>() {
      @Override
      protected TopicManagementResponse execute() throws FirebaseMessagingException {
        return messagingClient.unsubscribeFromTopic(topic, registrationTokens);
      }
    };
  }

  private static void checkRegistrationTokens(List<String> registrationTokens) {
    checkArgument(registrationTokens != null && !registrationTokens.isEmpty(),
        "registrationTokens list must not be null or empty");
    checkArgument(registrationTokens.size() <= 1000,
        "registrationTokens list must not contain more than 1000 elements");
    for (String token : registrationTokens) {
      checkArgument(!Strings.isNullOrEmpty(token),
          "registration tokens list must not contain null or empty strings");
    }
  }

  private static void checkTopic(String topic) {
    checkArgument(!Strings.isNullOrEmpty(topic), "topic must not be null or empty");
    checkArgument(topic.matches("^(/topics/)?(private/)?[a-zA-Z0-9-_.~%]+$"), "invalid topic name");
  }

  private static final String SERVICE_ID = FirebaseMessaging.class.getName();

  private static class FirebaseMessagingService extends FirebaseService<FirebaseMessaging> {

    FirebaseMessagingService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseMessaging(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of FirebaseMessaging
      // will now fail because calls to getOptions() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }
}
