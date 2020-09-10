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

import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;

import java.util.List;

/**
 * This class is the entry point for all server-side Firebase Cloud Messaging actions.
 *
 * <p>You can get an instance of FirebaseMessaging via {@link #getInstance(FirebaseApp)}, and
 * then use it to send messages or manage FCM topic subscriptions.
 */
public class FirebaseMessaging {

  private final FirebaseApp app;
  private final Supplier<? extends FirebaseMessagingClient> messagingClient;
  private final Supplier<? extends InstanceIdClient> instanceIdClient;

  private FirebaseMessaging(Builder builder) {
    this.app = checkNotNull(builder.firebaseApp);
    this.messagingClient = Suppliers.memoize(builder.messagingClient);
    this.instanceIdClient = Suppliers.memoize(builder.instanceIdClient);
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
   * @throws FirebaseMessagingException If an error occurs while handing the message off to FCM for
   *     delivery.
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
   * @throws FirebaseMessagingException If an error occurs while handing the message off to FCM for
   *     delivery.
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
    final FirebaseMessagingClient messagingClient = getMessagingClient();
    return new CallableOperation<String, FirebaseMessagingException>() {
      @Override
      protected String execute() throws FirebaseMessagingException {
        return messagingClient.send(message, dryRun);
      }
    };
  }

  /**
   * Sends all the messages in the given list via Firebase Cloud Messaging. Employs batching to
   * send the entire list as a single RPC call. Compared to the {@link #send(Message)} method, this
   * is a significantly more efficient way to send multiple messages.
   *
   * <p>The responses list obtained by calling {@link BatchResponse#getResponses()} on the return
   * value corresponds to the order of input messages.
   *
   * @param messages A non-null, non-empty list containing up to 500 messages.
   * @return A {@link BatchResponse} indicating the result of the operation.
   * @throws FirebaseMessagingException If an error occurs while handing the messages off to FCM for
   *     delivery. An exception here indicates a total failure -- i.e. none of the messages in the
   *     list could be sent. Partial failures are indicated by a {@link BatchResponse} return value.
   */
  public BatchResponse sendAll(
      @NonNull List<Message> messages) throws FirebaseMessagingException {
    return sendAll(messages, false);
  }

  /**
   * Sends all the messages in the given list via Firebase Cloud Messaging. Employs batching to
   * send the entire list as a single RPC call. Compared to the {@link #send(Message)} method, this
   * is a significantly more efficient way to send multiple messages.
   *
   * <p>If the {@code dryRun} option is set to true, the messages will not be actually sent. Instead
   * FCM performs all the necessary validations, and emulates the send operation.
   *
   * <p>The responses list obtained by calling {@link BatchResponse#getResponses()} on the return
   * value corresponds to the order of input messages.
   *
   * @param messages A non-null, non-empty list containing up to 500 messages.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A {@link BatchResponse} indicating the result of the operation.
   * @throws FirebaseMessagingException If an error occurs while handing the messages off to FCM for
   *     delivery. An exception here indicates a total failure -- i.e. none of the messages in the
   *     list could be sent. Partial failures are indicated by a {@link BatchResponse} return value.
   */
  public BatchResponse sendAll(
      @NonNull List<Message> messages, boolean dryRun) throws FirebaseMessagingException {
    return sendAllOp(messages, dryRun).call();
  }

  /**
   * Similar to {@link #sendAll(List)} but performs the operation asynchronously.
   *
   * @param messages A non-null, non-empty list containing up to 500 messages.
   * @return @return An {@code ApiFuture} that will complete with a {@link BatchResponse} when
   *     the messages have been sent.
   */
  public ApiFuture<BatchResponse> sendAllAsync(@NonNull List<Message> messages) {
    return sendAllAsync(messages, false);
  }

  /**
   * Similar to {@link #sendAll(List, boolean)} but performs the operation asynchronously.
   *
   * @param messages A non-null, non-empty list containing up to 500 messages.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return @return An {@code ApiFuture} that will complete with a {@link BatchResponse} when
   *     the messages have been sent, or when the emulation has finished.
   */
  public ApiFuture<BatchResponse> sendAllAsync(
      @NonNull List<Message> messages, boolean dryRun) {
    return sendAllOp(messages, dryRun).callAsync(app);
  }

  /**
   * Sends the given multicast message to all the FCM registration tokens specified in it.
   *
   * <p>This method uses the {@link #sendAll(List)} API under the hood to send the given
   * message to all the target recipients. The responses list obtained by calling
   * {@link BatchResponse#getResponses()} on the return value corresponds to the order of tokens
   * in the {@link MulticastMessage}.
   *
   * @param message A non-null {@link MulticastMessage}
   * @return A {@link BatchResponse} indicating the result of the operation.
   * @throws FirebaseMessagingException If an error occurs while handing the messages off to FCM for
   *     delivery. An exception here indicates a total failure -- i.e. the messages could not be
   *     delivered to any recipient. Partial failures are indicated by a {@link BatchResponse}
   *     return value.
   */
  public BatchResponse sendMulticast(
      @NonNull MulticastMessage message) throws FirebaseMessagingException {
    return sendMulticast(message, false);
  }

  /**
   * Sends the given multicast message to all the FCM registration tokens specified in it.
   *
   * <p>If the {@code dryRun} option is set to true, the message will not be actually sent. Instead
   * FCM performs all the necessary validations, and emulates the send operation.
   *
   * <p>This method uses the {@link #sendAll(List)} API under the hood to send the given
   * message to all the target recipients. The responses list obtained by calling
   * {@link BatchResponse#getResponses()} on the return value corresponds to the order of tokens
   * in the {@link MulticastMessage}.
   *
   * @param message A non-null {@link MulticastMessage}.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A {@link BatchResponse} indicating the result of the operation.
   * @throws FirebaseMessagingException If an error occurs while handing the messages off to FCM for
   *     delivery. An exception here indicates a total failure -- i.e. the messages could not be
   *     delivered to any recipient. Partial failures are indicated by a {@link BatchResponse}
   *     return value.
   */
  public BatchResponse sendMulticast(
      @NonNull MulticastMessage message, boolean dryRun) throws FirebaseMessagingException {
    checkNotNull(message, "multicast message must not be null");
    return sendAll(message.getMessageList(), dryRun);
  }

  /**
   * Similar to {@link #sendMulticast(MulticastMessage)} but performs the operation
   * asynchronously.
   *
   * @param message A non-null {@link MulticastMessage}.
   * @return An {@code ApiFuture} that will complete with a {@link BatchResponse} when
   *     the messages have been sent.
   */
  public ApiFuture<BatchResponse> sendMulticastAsync(@NonNull MulticastMessage message) {
    return sendMulticastAsync(message, false);
  }

  /**
   * Similar to {@link #sendMulticast(MulticastMessage, boolean)} but performs the operation
   * asynchronously.
   *
   * @param message A non-null {@link MulticastMessage}.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return An {@code ApiFuture} that will complete with a {@link BatchResponse} when
   *     the messages have been sent.
   */
  public ApiFuture<BatchResponse> sendMulticastAsync(
      @NonNull MulticastMessage message, boolean dryRun) {
    checkNotNull(message, "multicast message must not be null");
    return sendAllAsync(message.getMessageList(), dryRun);
  }

  private CallableOperation<BatchResponse, FirebaseMessagingException> sendAllOp(
      final List<Message> messages, final boolean dryRun) {

    final List<Message> immutableMessages = ImmutableList.copyOf(messages);
    checkArgument(!immutableMessages.isEmpty(), "messages list must not be empty");
    checkArgument(immutableMessages.size() <= 500,
        "messages list must not contain more than 500 elements");
    final FirebaseMessagingClient messagingClient = getMessagingClient();
    return new CallableOperation<BatchResponse,FirebaseMessagingException>() {
      @Override
      protected BatchResponse execute() throws FirebaseMessagingException {
        return messagingClient.sendAll(messages, dryRun);
      }
    };
  }

  @VisibleForTesting
  FirebaseMessagingClient getMessagingClient() {
    return messagingClient.get();
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
    final InstanceIdClient instanceIdClient = getInstanceIdClient();
    return new CallableOperation<TopicManagementResponse, FirebaseMessagingException>() {
      @Override
      protected TopicManagementResponse execute() throws FirebaseMessagingException {
        return instanceIdClient.subscribeToTopic(topic, registrationTokens);
      }
    };
  }

  /**
   * Unsubscribes a list of registration tokens from a topic.
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
    final InstanceIdClient instanceIdClient = getInstanceIdClient();
    return new CallableOperation<TopicManagementResponse, FirebaseMessagingException>() {
      @Override
      protected TopicManagementResponse execute() throws FirebaseMessagingException {
        return instanceIdClient.unsubscribeFromTopic(topic, registrationTokens);
      }
    };
  }

  @VisibleForTesting
  InstanceIdClient getInstanceIdClient() {
    return this.instanceIdClient.get();
  }

  private void checkRegistrationTokens(List<String> registrationTokens) {
    checkArgument(registrationTokens != null && !registrationTokens.isEmpty(),
        "registrationTokens list must not be null or empty");
    checkArgument(registrationTokens.size() <= 1000,
        "registrationTokens list must not contain more than 1000 elements");
    for (String token : registrationTokens) {
      checkArgument(!Strings.isNullOrEmpty(token),
          "registration tokens list must not contain null or empty strings");
    }
  }

  private void checkTopic(String topic) {
    checkArgument(!Strings.isNullOrEmpty(topic), "topic must not be null or empty");
    checkArgument(topic.matches("^(/topics/)?(private/)?[a-zA-Z0-9-_.~%]+$"), "invalid topic name");
  }

  private static final String SERVICE_ID = FirebaseMessaging.class.getName();

  private static class FirebaseMessagingService extends FirebaseService<FirebaseMessaging> {

    FirebaseMessagingService(FirebaseApp app) {
      super(SERVICE_ID, FirebaseMessaging.fromApp(app));
    }
  }

  private static FirebaseMessaging fromApp(final FirebaseApp app) {
    return FirebaseMessaging.builder()
        .setFirebaseApp(app)
        .setMessagingClient(new Supplier<FirebaseMessagingClient>() {
          @Override
          public FirebaseMessagingClient get() {
            return FirebaseMessagingClientImpl.fromApp(app);
          }
        })
        .setInstanceIdClient(new Supplier<InstanceIdClient>() {
          @Override
          public InstanceIdClientImpl get() {
            return InstanceIdClientImpl.fromApp(app);
          }
        })
        .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    private FirebaseApp firebaseApp;
    private Supplier<? extends FirebaseMessagingClient> messagingClient;
    private Supplier<? extends InstanceIdClient> instanceIdClient;

    private Builder() { }

    Builder setFirebaseApp(FirebaseApp firebaseApp) {
      this.firebaseApp = firebaseApp;
      return this;
    }

    Builder setMessagingClient(Supplier<? extends FirebaseMessagingClient> messagingClient) {
      this.messagingClient = messagingClient;
      return this;
    }

    Builder setInstanceIdClient(Supplier<? extends InstanceIdClient> instanceIdClient) {
      this.instanceIdClient = instanceIdClient;
      return this;
    }

    FirebaseMessaging build() {
      return new FirebaseMessaging(this);
    }
  }
}
