package com.google.firebase.messaging.internal;

import com.google.api.client.util.Key;

/**
 * The DTO for parsing success responses from the FCM service.
 */
public class MessagingServiceResponse {

  @Key("name")
  private String messageId;

  public String getMessageId() {
    return this.messageId;
  }
}
