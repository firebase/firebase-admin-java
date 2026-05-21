/*
 * Copyright 2019 Google LLC
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

import com.google.api.client.util.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a message that can be sent to multiple devices via Firebase Cloud Messaging (FCM).
 * Contains payload information as well as the list of device registration tokens to which the
 * message should be sent. A single {@code MulticastMessage} may contain up to 500 registration
 * tokens.
 *
 * <p>Instances of this class are thread-safe and immutable. Use {@link MulticastMessage.Builder}
 * to create new instances. See {@link FirebaseMessaging#sendMulticast(MulticastMessage)} for
 * details on how to send the message to FCM for multicast delivery.
 *
 * <p>This class and the associated Builder retain the order of tokens. Therefore the order of
 * the responses list obtained by calling {@link BatchResponse#getResponses()} on the return value
 * of {@link FirebaseMessaging#sendMulticast(MulticastMessage)} corresponds to the order in which
 * tokens were added to the {@link MulticastMessage.Builder}.
 */
public class MulticastMessage {

  @Deprecated
  private final List<String> tokens;
  private final List<String> fids;
  private final Map<String, String> data;
  private final Notification notification;
  private final AndroidConfig androidConfig;
  private final WebpushConfig webpushConfig;
  private final ApnsConfig apnsConfig;
  private final FcmOptions fcmOptions;

  private MulticastMessage(Builder builder) {
    this.tokens = builder.tokens.build();
    this.fids = builder.fids.build();
    int tokensSize = this.tokens.size();
    int fidsSize = this.fids.size();
    checkArgument(tokensSize + fidsSize > 0, "at least one token or fid must be specified");
    checkArgument(tokensSize + fidsSize <= 500,
        "no more than 500 tokens and fids combined can be specified");
    for (String token : this.tokens) {
      checkArgument(!Strings.isNullOrEmpty(token), "none of the tokens can be null or empty");
    }
    for (String fid : this.fids) {
      checkArgument(!Strings.isNullOrEmpty(fid), "none of the fids can be null or empty");
    }
    this.data = builder.data.isEmpty() ? null : ImmutableMap.copyOf(builder.data);
    this.notification = builder.notification;
    this.androidConfig = builder.androidConfig;
    this.webpushConfig = builder.webpushConfig;
    this.apnsConfig = builder.apnsConfig;
    this.fcmOptions = builder.fcmOptions;
  }

  List<Message> getMessageList() {
    ImmutableList.Builder<Message> messages = ImmutableList.builder();

    if (!this.tokens.isEmpty()) {
      Message.Builder tokenBuilder = getMetadataBuilder();
      for (String token : this.tokens) {
        messages.add(tokenBuilder.setToken(token).build());
      }
    }

    if (!this.fids.isEmpty()) {
      Message.Builder fidBuilder = getMetadataBuilder();
      for (String fid : this.fids) {
        messages.add(fidBuilder.setFid(fid).build());
      }
    }

    return messages.build();
  }

  private Message.Builder getMetadataBuilder() {
    Message.Builder builder = Message.builder()
        .setNotification(this.notification)
        .setAndroidConfig(this.androidConfig)
        .setApnsConfig(this.apnsConfig)
        .setWebpushConfig(this.webpushConfig)
        .setFcmOptions(this.fcmOptions);
    if (this.data != null) {
      builder.putAllData(this.data);
    }
    return builder;
  }

  /**
   * Creates a new {@link MulticastMessage.Builder}.
   *
   * @return A {@link MulticastMessage.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    @Deprecated
    private final ImmutableList.Builder<String> tokens = ImmutableList.builder();
    private final ImmutableList.Builder<String> fids = ImmutableList.builder();
    private final Map<String, String> data = new HashMap<>();
    private Notification notification;
    private AndroidConfig androidConfig;
    private WebpushConfig webpushConfig;
    private ApnsConfig apnsConfig;
    private FcmOptions fcmOptions;

    private Builder() {}

    /**
     * Adds a token to which the message should be sent. Up to 500 tokens
     * and FIDs combined can be specified on a single instance of
     * {@link MulticastMessage}.
     *
     * @param token A non-null, non-empty Firebase device registration token.
     * @return This builder.
     * @deprecated Use {@link #addFid(String)} instead.
     */
    @Deprecated
    public Builder addToken(@NonNull String token) {
      this.tokens.add(token);
      return this;
    }

    /**
     * Adds a Firebase Installation ID (FID) to which the message should be sent.
     * Up to 500 tokens and FIDs combined can be specified on a single instance
     * of {@link MulticastMessage}.
     *
     * @param fid A non-null, non-empty Firebase Installation ID.
     * @return This builder.
     */
    public Builder addFid(@NonNull String fid) {
      this.fids.add(fid);
      return this;
    }

    /**
     * Adds a collection of tokens to which the message should be sent. Up to 500
     * tokens and FIDs combined can be specified on a single instance of
     * {@link MulticastMessage}.
     *
     * @param tokens Collection of Firebase device registration tokens.
     * @return This builder.
     * @deprecated Use {@link #addAllFids(Collection)} instead.
     */
    @Deprecated
    public Builder addAllTokens(@NonNull Collection<String> tokens) {
      this.tokens.addAll(tokens);
      return this;
    }

    /**
     * Adds a collection of Firebase Installation IDs (FIDs) to which the message
     * should be sent. Up to 500 tokens and FIDs combined can be specified on a
     * single instance of {@link MulticastMessage}.
     *
     * @param fids Collection of Firebase Installation IDs.
     * @return This builder.
     */
    public Builder addAllFids(@NonNull Collection<String> fids) {
      this.fids.addAll(fids);
      return this;
    }

    /**
     * Sets the notification information to be included in the message.
     *
     * @param notification A {@link Notification} instance.
     * @return This builder.
     */
    public Builder setNotification(Notification notification) {
      this.notification = notification;
      return this;
    }

    /**
     * Sets the Android-specific information to be included in the message.
     *
     * @param androidConfig An {@link AndroidConfig} instance.
     * @return This builder.
     */
    public Builder setAndroidConfig(AndroidConfig androidConfig) {
      this.androidConfig = androidConfig;
      return this;
    }

    /**
     * Sets the Webpush-specific information to be included in the message.
     *
     * @param webpushConfig A {@link WebpushConfig} instance.
     * @return This builder.
     */
    public Builder setWebpushConfig(WebpushConfig webpushConfig) {
      this.webpushConfig = webpushConfig;
      return this;
    }

    /**
     * Sets the information specific to APNS (Apple Push Notification Service).
     *
     * @param apnsConfig An {@link ApnsConfig} instance.
     * @return This builder.
     */
    public Builder setApnsConfig(ApnsConfig apnsConfig) {
      this.apnsConfig = apnsConfig;
      return this;
    }

    /**
     * Sets the {@link FcmOptions}, which can be overridden by the platform-specific {@code
     * fcm_options} fields.
     */
    public Builder setFcmOptions(FcmOptions fcmOptions) {
      this.fcmOptions = fcmOptions;
      return this;
    }

    /**
     * Adds the given key-value pair to the message as a data field. Key or the value may not be
     * null.
     *
     * @param key Name of the data field. Must not be null.
     * @param value Value of the data field. Must not be null.
     * @return This builder.
     */
    public Builder putData(@NonNull String key, @NonNull String value) {
      this.data.put(key, value);
      return this;
    }

    /**
     * Adds all the key-value pairs in the given map to the message as data fields. None of the
     * keys or values may be null.
     *
     * @param map A non-null map of data fields. Map must not contain null keys or values.
     * @return This builder.
     */
    public Builder putAllData(@NonNull Map<String, String> map) {
      this.data.putAll(map);
      return this;
    }

    /**
     * Creates a new {@link MulticastMessage} instance from the parameters set on this builder.
     *
     * @return A new {@link MulticastMessage} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public MulticastMessage build() {
      return new MulticastMessage(this);
    }
  }
}
