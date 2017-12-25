package com.google.firebase.messaging;

import com.google.api.client.util.Key;

public class Notification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;

  public Notification(String title, String body) {
    this.title = title;
    this.body = body;
  }

}
