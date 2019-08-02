package com.google.firebase.messaging;

import com.google.api.client.util.Key;

/**
 * Represents options for features provided by the FCM SDK for Web.
 * Can be included in {@link WebpushConfig}. Instances of this class are thread-safe and immutable.
 */
public final class WebpushFcmOptions {

  @Key("link")
  private final String link;

  private WebpushFcmOptions(Builder builder) {
    this.link = builder.link;
  }

  /**
   * Creates a new {@code WebpushFcmOptions} using given link.
   *
   * @param link The link to open when the user clicks on the notification.
   *             For all URL values, HTTPS is required.
   */
  public static WebpushFcmOptions withLink(String link) {
    return new Builder().setLink(link).build();
  }

  /**
   * Creates a new {@link WebpushFcmOptions.Builder}.
   *
   * @return An {@link WebpushFcmOptions.Builder} instance.
   */
  public static Builder builder() {
    return new WebpushFcmOptions.Builder();
  }

  public static class Builder {

    private String link;

    private Builder() {}

    /**
     * @param link The link to open when the user clicks on the notification.
     *             For all URL values, HTTPS is required.
     * @return This builder
     */
    public Builder setLink(String link) {
      this.link = link;
      return this;
    }

    /**
     * Creates a new {@link WebpushFcmOptions} instance from the parameters set on this builder.
     *
     * @return A new {@link WebpushFcmOptions} instance.
     */
    public WebpushFcmOptions build() {
      return new WebpushFcmOptions(this);
    }
  }
}
