package com.google.firebase.messaging;

import com.google.api.client.util.Key;

/**
 * Represents options for features provided by the FCM SDK for Web.
 * Can be included in {@link WebpushConfig}. Instances of this class are thread-safe and immutable.
 */
public class WebpushFcmOptions {

  @Key("link")
  private final String link;

  /**
   * Creates a new {@code WebpushFcmOptions} using given link.
   *
   * @param link The link to open when the user clicks on the notification.
   *             For all URL values, HTTPS is required.
   */
  public WebpushFcmOptions(String link) {
    this.link = link;
  }
}
