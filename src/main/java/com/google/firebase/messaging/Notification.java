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

import com.google.api.client.util.Key;

/**
 * Represents the notification parameters that can be included in a {@link Message}. Instances
 * of this class are thread-safe and immutable.
 */
public class Notification {

  @Key("title")
  private final String title;

  @Key("body")
  private final String body;

  /**
   * Creates a new {@code Notification} using the given title and body.
   *
   * @param title Title of the notification.
   * @param body Body of the notification.
   */
  public Notification(String title, String body) {
    this.title = title;
    this.body = body;
  }

}
