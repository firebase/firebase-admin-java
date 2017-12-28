package com.google.firebase.messaging;

import com.google.api.client.util.Key;

public class WebpushNotification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;

  @Key("icon")
  private final String icon;

  public WebpushNotification(String title, String body) {
    this(title, body, null);
  }

  public WebpushNotification(String title, String body, String icon) {
    this.title = title;
    this.body = body;
    this.icon = icon;
  }
}
