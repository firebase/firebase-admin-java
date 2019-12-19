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

package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Represents the Android-specific notification options that can be included in a {@link Message}.
 * Instances of this class are thread-safe and immutable.
 */
public class AndroidNotification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;

  @Key("icon")
  private final String icon;

  @Key("color")
  private final String color;

  @Key("sound")
  private final String sound;

  @Key("tag")
  private final String tag;

  @Key("click_action")
  private final String clickAction;

  @Key("body_loc_key")
  private final String bodyLocKey;

  @Key("body_loc_args")
  private final List<String> bodyLocArgs;

  @Key("title_loc_key")
  private final String titleLocKey;

  @Key("title_loc_args")
  private final List<String> titleLocArgs;
  
  @Key("channel_id")
  private final String channelId;
  
  @Key("image")
  private final String image;
 
  @Key("ticker")
  private final String ticker;

  @Key("sticky")
  private final Boolean sticky;

  @Key("event_time")
  private final String eventTime;

  @Key("local_only")
  private final Boolean localOnly;

  @Key("notification_priority")
  private final String priority;

  @Key("vibrate_timings")
  private final List<String> vibrateTimings;

  @Key("default_vibrate_timings")
  private final Boolean defaultVibrateTimings;

  @Key("default_sound")
  private final Boolean defaultSound;

  @Key("light_settings")
  private final LightSettings lightSettings;
  
  @Key("default_light_settings")
  private final Boolean defaultLightSettings;

  @Key("visibility")
  private final String visibility;

  @Key("notification_count")
  private final Integer notificationCount;

  private static final Map<Priority, String> PRIORITY_MAP = 
      ImmutableMap.<Priority, String>builder()
          .put(Priority.MIN, "PRIORITY_MIN")
          .put(Priority.LOW, "PRIORITY_LOW")
          .put(Priority.DEFAULT, "PRIORITY_DEFAULT")
          .put(Priority.HIGH, "PRIORITY_HIGH")
          .put(Priority.MAX, "PRIORITY_MAX")
          .build();
  
  private AndroidNotification(Builder builder) {
    this.title = builder.title;
    this.body = builder.body;
    this.icon = builder.icon;
    if (builder.color != null) {
      checkArgument(builder.color.matches("^#[0-9a-fA-F]{6}$"),
          "color must be in the form #RRGGBB");
    }
    this.color = builder.color;
    this.sound = builder.sound;
    this.tag = builder.tag;
    this.clickAction = builder.clickAction;
    this.bodyLocKey = builder.bodyLocKey;
    if (!builder.bodyLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.bodyLocKey),
          "bodyLocKey is required when specifying bodyLocArgs");
      this.bodyLocArgs = ImmutableList.copyOf(builder.bodyLocArgs);
    } else {
      this.bodyLocArgs = null;
    }

    this.titleLocKey = builder.titleLocKey;
    if (!builder.titleLocArgs.isEmpty()) {
      checkArgument(!Strings.isNullOrEmpty(builder.titleLocKey),
          "titleLocKey is required when specifying titleLocArgs");
      this.titleLocArgs = ImmutableList.copyOf(builder.titleLocArgs);
    } else {
      this.titleLocArgs = null;
    }
    this.channelId = builder.channelId;
    this.image = builder.image;    
    this.ticker = builder.ticker;
    this.sticky = builder.sticky;
    this.eventTime = builder.eventTime;
    this.localOnly = builder.localOnly;
    if (builder.priority != null) {
      this.priority = builder.priority.toString();
    } else {
      this.priority = null;
    }
    if (!builder.vibrateTimings.isEmpty()) {
      this.vibrateTimings = ImmutableList.copyOf(builder.vibrateTimings);
    } else {
      this.vibrateTimings = null;
    }
    this.defaultVibrateTimings = builder.defaultVibrateTimings;
    this.defaultSound = builder.defaultSound;
    this.lightSettings = builder.lightSettings;
    this.defaultLightSettings = builder.defaultLightSettings;
    if (builder.visibility != null) {
      this.visibility = builder.visibility.name().toLowerCase();
    } else {
      this.visibility = null;
    }
    if (builder.notificationCount != null) {
      checkArgument(builder.notificationCount >= 0, 
          "notificationCount if specified must be zero or positive valued");
    }
    this.notificationCount = builder.notificationCount;
  }

  public enum Priority {
    MIN,
    LOW,
    DEFAULT,
    HIGH,
    MAX;

    @Override
    public String toString() {
      return PRIORITY_MAP.get(this);
    }
  }
  
  public enum Visibility {
    PRIVATE,
    PUBLIC,
    SECRET,
  }

  /**
   * Creates a new {@link AndroidNotification.Builder}.
   *
   * @return A {@link AndroidNotification.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String title;
    private String body;
    private String icon;
    private String color;
    private String sound;
    private String tag;
    private String clickAction;
    private String bodyLocKey;
    private List<String> bodyLocArgs = new ArrayList<>();
    private String titleLocKey;
    private List<String> titleLocArgs = new ArrayList<>();
    private String channelId;
    private String image;
    private Integer notificationCount;
    private String ticker;
    private Boolean sticky;
    private String eventTime;
    private Boolean localOnly;
    private Priority priority;
    private List<String> vibrateTimings = new ArrayList<>();
    private Boolean defaultVibrateTimings;
    private Boolean defaultSound;
    private LightSettings lightSettings;
    private Boolean defaultLightSettings;
    private Visibility visibility;

    private Builder() {}

    /**
     * Sets the title of the Android notification. When provided, overrides the title set
     * via {@link Notification}.
     *
     * @param title Title of the notification.
     * @return This builder.
     */
    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    /**
     * Sets the body of the Android notification. When provided, overrides the body set
     * via {@link Notification}.
     *
     * @param body Body of the notification.
     * @return This builder.
     */
    public Builder setBody(String body) {
      this.body = body;
      return this;
    }

    /**
     * Sets the icon of the Android notification.
     *
     * @param icon Icon resource for the notification.
     * @return This builder.
     */
    public Builder setIcon(String icon) {
      this.icon = icon;
      return this;
    }

    /**
     * Sets the notification icon color.
     *
     * @param color Color specified in the {@code #rrggbb} format.
     * @return This builder.
     */
    public Builder setColor(String color) {
      this.color = color;
      return this;
    }

    /**
     * Sets the sound to be played when the device receives the notification.
     *
     * @param sound File name of the sound resource or "default".
     * @return This builder.
     */
    public Builder setSound(String sound) {
      this.sound = sound;
      return this;
    }

    /**
     * Sets the notification tag. This is an identifier used to replace existing notifications in
     * the notification drawer. If not specified, each request creates a new notification.
     *
     * @param tag Notification tag.
     * @return This builder.
     */
    public Builder setTag(String tag) {
      this.tag = tag;
      return this;
    }

    /**
     * Sets the action associated with a user click on the notification. If specified, an activity
     * with a matching Intent Filter is launched when a user clicks on the notification.
     *
     * @param clickAction Click action name.
     * @return This builder.
     */
    public Builder setClickAction(String clickAction) {
      this.clickAction = clickAction;
      return this;
    }

    /**
     * Sets the key of the body string in the app's string resources to use to localize the body
     * text.
     *
     * @param bodyLocKey Resource key string.
     * @return This builder.
     */
    public Builder setBodyLocalizationKey(String bodyLocKey) {
      this.bodyLocKey = bodyLocKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addBodyLocalizationArg(@NonNull String arg) {
      this.bodyLocArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code bodyLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllBodyLocalizationArgs(@NonNull List<String> args) {
      this.bodyLocArgs.addAll(args);
      return this;
    }

    /**
     * Sets the key of the title string in the app's string resources to use to localize the title
     * text.
     *
     * @param titleLocKey Resource key string.
     * @return This builder.
     */
    public Builder setTitleLocalizationKey(String titleLocKey) {
      this.titleLocKey = titleLocKey;
      return this;
    }

    /**
     * Adds a resource key string that will be used in place of the format specifiers in
     * {@code titleLocKey}.
     *
     * @param arg Resource key string.
     * @return This builder.
     */
    public Builder addTitleLocalizationArg(@NonNull String arg) {
      this.titleLocArgs.add(arg);
      return this;
    }

    /**
     * Adds a list of resource keys that will be used in place of the format specifiers in
     * {@code titleLocKey}.
     *
     * @param args List of resource key strings.
     * @return This builder.
     */
    public Builder addAllTitleLocalizationArgs(@NonNull List<String> args) {
      this.titleLocArgs.addAll(args);
      return this;
    }

    /**
     * Sets the Android notification channel ID (new in Android O). The app must create a channel
     * with this channel ID before any notification with this channel ID is received. If you 
     * don't send this channel ID in the request, or if the channel ID provided has not yet been
     * created by the app, FCM uses the channel ID specified in the app manifest.
     *
     * @param channelId The notification's channel ID.
     * @return This builder.
     */
    public Builder setChannelId(String channelId) {
      this.channelId = channelId;
      return this;
    }

    /**
     * Sets the URL of the image that is going to be displayed in the notification. When provided, 
     * overrides the imageUrl set via {@link Notification}.
     *
     * @param imageUrl URL of the image that is going to be displayed in the notification.
     * @return This builder.
     */
    public Builder setImage(String imageUrl) {
      this.image = imageUrl;
      return this;
    }

    /**
     * Sets the "ticker" text, which is sent to accessibility services. Prior to API level 21
     * (Lollipop), sets the text that is displayed in the status bar when the notification 
     * first arrives.
     *
     * @param ticker Ticker name.
     * @return This builder.
     */
    public Builder setTicker(String ticker) {
      this.ticker = ticker;
      return this;
    }

    /**
     * Sets the sticky flag. When set to false or unset, the notification is automatically 
     * dismissed when the user clicks it in the panel. When set to true, the notification 
     * persists even when the user clicks it.
     *
     * @param sticky The sticky flag
     * @return This builder.
     */
    public Builder setSticky(boolean sticky) {
      this.sticky = sticky;
      return this;
    }

    /**
     * For notifications that inform users about events with an absolute time reference, sets
     * the time that the event in the notification occurred in milliseconds. Notifications
     * in the panel are sorted by this time. The time is formatted in RFC3339 UTC "Zulu" 
     * format, accurate to nanoseconds. Example: "2014-10-02T15:01:23.045123456Z". Note that 
     * since the time is in milliseconds, the last section of the time representation always
     * has 6 leading zeros.
     *
     * @param eventTimeInMillis The event time in milliseconds
     * @return This builder.
     */
    public Builder setEventTimeInMillis(long eventTimeInMillis) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS000000'Z'");
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      this.eventTime = dateFormat.format(new Date(eventTimeInMillis));
      return this;
    }

    /**
     * Sets whether or not this notification is relevant only to the current device. Some 
     * notifications can be bridged to other devices for remote display, such as a Wear 
     * OS watch. This hint can be set to recommend this notification not be bridged.
     *
     * @param localOnly The "local only" flag
     * @return This builder.
     */
    public Builder setLocalOnly(boolean localOnly) {
      this.localOnly = localOnly;
      return this;
    }

    /**
     * Sets the relative priority for this notification. Priority is an indication of how much of 
     * the user's attention should be consumed by this notification. Low-priority notifications 
     * may be hidden from the user in certain situations, while the user might be interrupted 
     * for a higher-priority notification.
     *
     * @param priority The priority value, one of the values in {MIN, LOW, DEFAULT, HIGH, MAX}
     * @return This builder.
     */
    public Builder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }

    /**
     * Sets a list of vibration timings in milliseconds in the array to use. The first value in the 
     * array indicates the duration to wait before turning the vibrator on. The next value 
     * indicates the duration to keep the vibrator on. Subsequent values alternate between 
     * duration to turn the vibrator off and to turn the vibrator on. If {@code vibrate_timings} 
     * is set and {@code default_vibrate_timings} is set to true, the default value is used instead
     * of the user-specified {@code vibrate_timings}.
     * A duration in seconds with up to nine fractional digits, terminated by 's'. Example: "3.5s".
     *
     * @param vibrateTimingsInMillis List of vibration time in milliseconds
     * @return This builder.
     */
    public Builder setVibrateTimingsInMillis(long[] vibrateTimingsInMillis) {
      List<String> list = new ArrayList<>();
      for (long value : vibrateTimingsInMillis) {
        checkArgument(value >= 0, "elements in vibrateTimingsInMillis must not be negative");
        long seconds = TimeUnit.MILLISECONDS.toSeconds(value);
        long subsecondNanos = TimeUnit.MILLISECONDS.toNanos(value - seconds * 1000L);
        if (subsecondNanos > 0) {
          list.add(String.format("%d.%09ds", seconds, subsecondNanos));
        } else {
          list.add(String.format("%ds", seconds));
        }   
      }
      this.vibrateTimings = ImmutableList.copyOf(list);  
      return this;
    }

    /**
     * Sets the whether to use the default vibration timings. If set to true, use the Android 
     * framework's default vibrate pattern for the notification. Default values are specified 
     * in {@code config.xml}. If {@code default_vibrate_timings} is set to true and 
     * {@code vibrate_timings} is also set, the default value is used instead of the 
     * user-specified {@code vibrate_timings}.
     *
     * @param defaultVibrateTimings The flag indicating whether to use the default vibration timings
     * @return This builder.
     */
    public Builder setDefaultVibrateTimings(boolean defaultVibrateTimings) {
      this.defaultVibrateTimings = defaultVibrateTimings;
      return this;
    }

    /**
     * Sets the whether to use the default sound. If set to true, use the Android framework's 
     * default sound for the notification. Default values are specified in config.xml.
     *
     * @param defaultSound The flag indicating whether to use the default sound
     * @return This builder.
     */
    public Builder setDefaultSound(boolean defaultSound) {
      this.defaultSound = defaultSound;
      return this;
    }

    /**
     * Sets the settings to control the notification's LED blinking rate and color if LED is 
     * available on the device. The total blinking time is controlled by the OS.
     *
     * @param lightSettings The light settings to use
     * @return This builder.
     */
    public Builder setLightSettings(LightSettings lightSettings) {
      this.lightSettings = lightSettings;
      return this;
    }

    /**
     * Sets the whether to use the default light settings. If set to true, use the Android
     * framework's default LED light settings for the notification. Default values are 
     * specified in config.xml. If {@code default_light_settings} is set to true and 
     * {@code light_settings} is also set, the user-specified {@code light_settings} is used
     * instead of the default value.
     *
     * @param defaultLightSettings The flag indicating whether to use the default light 
     *     settings
     * @return This builder.
     */
    public Builder setDefaultLightSettings(boolean defaultLightSettings) {
      this.defaultLightSettings = defaultLightSettings;
      return this;
    }

    /**
     * Sets the visibility of this notification.
     *
     * @param visibility The visibility value. one of the values in {PRIVATE, PUBLIC, SECRET}
     * @return This builder.
     */
    public Builder setVisibility(Visibility visibility) {
      this.visibility = visibility;
      return this;
    }

    /**
     * Sets the number of items this notification represents. May be displayed as a badge 
     * count for launchers that support badging. 
     * If not invoked then notification count is left unchanged.
     * For example, this might be useful if you're using just one notification to represent
     * multiple new messages but you want the count here to represent the number of total
     * new messages. If zero or unspecified, systems that support badging use the default, 
     * which is to increment a number displayed on 
     * the long-press menu each time a new notification arrives.
     *
     * @param notificationCount Zero or positive value. Zero indicates leave unchanged.
     * @return This builder.
     */
    public Builder setNotificationCount(int notificationCount) {
      this.notificationCount = notificationCount;
      return this;
    }

    /**
     * Creates a new {@link AndroidNotification} instance from the parameters set on this builder.
     *
     * @return A new {@link AndroidNotification} instance.
     * @throws IllegalArgumentException If any of the parameters set on the builder are invalid.
     */
    public AndroidNotification build() {
      return new AndroidNotification(this);
    }
  }
}
