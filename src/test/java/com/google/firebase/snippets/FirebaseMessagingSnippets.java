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

package com.google.firebase.snippets;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.TopicManagementResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import java.util.Arrays;
import java.util.List;

/**
 * Firebase Cloud Messaging (FCM) snippets for documentation.
 */
public class FirebaseMessagingSnippets {

  public void sendToToken() throws FirebaseMessagingException {
    // [START send_to_token]
    // This registration token comes from the client FCM SDKs.
    String registrationToken = "YOUR_REGISTRATION_TOKEN";

    // See documentation on defining a message payload.
    Message message = Message.builder()
        .putData("score", "850")
        .putData("time", "2:45")
        .setToken(registrationToken)
        .build();

    // Send a message to the device corresponding to the provided
    // registration token.
    String response = FirebaseMessaging.getInstance().send(message);
    // Response is a message ID string.
    System.out.println("Successfully sent message: " + response);
    // [END send_to_token]
  }

  public void sendToTopic() throws FirebaseMessagingException {
    // [START send_to_topic]
    // The topic name can be optionally prefixed with "/topics/".
    String topic = "highScores";

    // See documentation on defining a message payload.
    Message message = Message.builder()
        .putData("score", "850")
        .putData("time", "2:45")
        .setTopic(topic)
        .build();

    // Send a message to the devices subscribed to the provided topic.
    String response = FirebaseMessaging.getInstance().send(message);
    // Response is a message ID string.
    System.out.println("Successfully sent message: " + response);
    // [END send_to_topic]
  }

  public void sendToCondition() throws FirebaseMessagingException {
    // [START send_to_condition]
    // Define a condition which will send to devices which are subscribed
    // to either the Google stock or the tech industry topics.
    String condition = "'stock-GOOG' in topics || 'industry-tech' in topics";

    // See documentation on defining a message payload.
    Message message = Message.builder()
        .setNotification(new Notification(
            "$GOOG up 1.43% on the day",
            "$GOOG gained 11.80 points to close at 835.67, up 1.43% on the day."))
        .setCondition(condition)
        .build();

    // Send a message to devices subscribed to the combination of topics
    // specified by the provided condition.
    String response = FirebaseMessaging.getInstance().send(message);
    // Response is a message ID string.
    System.out.println("Successfully sent message: " + response);
    // [END send_to_condition]
  }

  public void sendDryRun() throws FirebaseMessagingException {
    Message message = Message.builder()
        .putData("score", "850")
        .putData("time", "2:45")
        .setToken("token")
        .build();

    // [START send_dry_run]
    // Send a message in the dry run mode.
    boolean dryRun = true;
    String response = FirebaseMessaging.getInstance().send(message, dryRun);
    // Response is a message ID string.
    System.out.println("Dry run successful: " + response);
    // [END send_dry_run]
  }

  public Message androidMessage() {
    // [START android_message]
    Message message = Message.builder()
        .setAndroidConfig(AndroidConfig.builder()
            .setTtl(3600 * 1000) // 1 hour in milliseconds
            .setPriority(AndroidConfig.Priority.NORMAL)
            .setNotification(AndroidNotification.builder()
                .setTitle("$GOOG up 1.43% on the day")
                .setBody("$GOOG gained 11.80 points to close at 835.67, up 1.43% on the day.")
                .setIcon("stock_ticker_update")
                .setColor("#f45342")
                .build())
            .build())
        .setTopic("industry-tech")
        .build();
    // [END android_message]
    return message;
  }

  public Message apnsMessage() {
    // [START apns_message]
    Message message = Message.builder()
        .setApnsConfig(ApnsConfig.builder()
            .putHeader("apns-priority", "10")
            .setAps(Aps.builder()
                .setAlert(ApsAlert.builder()
                    .setTitle("$GOOG up 1.43% on the day")
                    .setBody("$GOOG gained 11.80 points to close at 835.67, up 1.43% on the day.")
                    .build())
                .setBadge(42)
                .build())
            .build())
        .setTopic("industry-tech")
        .build();
    // [END apns_message]
    return message;
  }

  public Message webpushMessage() {
    // [START webpush_message]
    Message message = Message.builder()
        .setWebpushConfig(WebpushConfig.builder()
            .setNotification(new WebpushNotification(
                "$GOOG up 1.43% on the day",
                "$GOOG gained 11.80 points to close at 835.67, up 1.43% on the day.",
                "https://my-server/icon.png"))
            .build())
        .setTopic("industry-tech")
        .build();
    // [END webpush_message]
    return message;
  }

  public Message allPlatformsMessage() {
    // [START multi_platforms_message]
    Message message = Message.builder()
        .setNotification(new Notification(
            "$GOOG up 1.43% on the day",
            "$GOOG gained 11.80 points to close at 835.67, up 1.43% on the day."))
        .setAndroidConfig(AndroidConfig.builder()
            .setTtl(3600 * 1000)
            .setNotification(AndroidNotification.builder()
                .setIcon("stock_ticker_update")
                .setColor("#f45342")
                .build())
            .build())
        .setApnsConfig(ApnsConfig.builder()
            .setAps(Aps.builder()
                .setBadge(42)
                .build())
            .build())
        .setTopic("industry-tech")
        .build();
    // [END multi_platforms_message]
    return message;
  }

  public void subscribeToTopic() throws FirebaseMessagingException {
    String topic = "highScores";
    // [START subscribe]
    // These registration tokens come from the client FCM SDKs.
    List<String> registrationTokens = Arrays.asList(
        "YOUR_REGISTRATION_TOKEN_1",
        // ...
        "YOUR_REGISTRATION_TOKEN_n"
    );

    // Subscribe the devices corresponding to the registration tokens to the
    // topic.
    TopicManagementResponse response = FirebaseMessaging.getInstance().subscribeToTopic(
        registrationTokens, topic);
    // See the TopicManagementResponse reference documentation
    // for the contents of response.
    System.out.println(response.getSuccessCount() + " tokens were subscribed successfully");
    // [END subscribe]
  }

  public void unsubscribeFromTopic() throws FirebaseMessagingException {
    String topic = "highScores";
    // [START unsubscribe]
    // These registration tokens come from the client FCM SDKs.
    List<String> registrationTokens = Arrays.asList(
        "YOUR_REGISTRATION_TOKEN_1",
        // ...
        "YOUR_REGISTRATION_TOKEN_n"
    );

    // Unsubscribe the devices corresponding to the registration tokens from
    // the topic.
    TopicManagementResponse response = FirebaseMessaging.getInstance().unsubscribeFromTopic(
        registrationTokens, topic);
    // See the TopicManagementResponse reference documentation
    // for the contents of response.
    System.out.println(response.getSuccessCount() + " tokens were unsubscribed successfully");
    // [END unsubscribe]
  }

}