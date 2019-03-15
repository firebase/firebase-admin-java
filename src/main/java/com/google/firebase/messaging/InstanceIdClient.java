package com.google.firebase.messaging;

import java.util.List;

interface InstanceIdClient {

  TopicManagementResponse subscribeToTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException;

  TopicManagementResponse unsubscribeFromTopic(
      String topic, List<String> registrationTokens) throws FirebaseMessagingException;

}
