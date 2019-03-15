package com.google.firebase.messaging;

import java.util.List;

/**
 * An interface for managing FCM topic subscriptions.
 */
interface InstanceIdClient {

  /**
   * Subscribes a list of registration tokens to a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens.
   * @param topic Name of the topic to subscribe to. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  TopicManagementResponse subscribeToTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException;

  /**
   * Unsubscribes a list of registration tokens from a topic.
   *
   * @param registrationTokens A non-null, non-empty list of device registration tokens.
   * @param topic Name of the topic to unsubscribe from. May contain the {@code /topics/} prefix.
   * @return A {@link TopicManagementResponse}.
   */
  TopicManagementResponse unsubscribeFromTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException;

}
