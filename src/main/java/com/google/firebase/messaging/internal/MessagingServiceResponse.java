package com.google.firebase.messaging.internal;

import com.google.api.client.util.Key;

/**
 * MessagingServiceResponse
 */
public class MessagingServiceResponse {

  @Key("name")
  private String name;

  public String getName() {
    return this.name;
  }
}
