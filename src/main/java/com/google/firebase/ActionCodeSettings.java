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

package com.google.firebase;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URL;

public final class ActionCodeSettings {

  private final String url;
  private final boolean handleCodeInApp;
  private final String dynamicLinkDomain;

  private ActionCodeSettings(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.url), "URL must not be null or empty");
    try {
      new URL(builder.url);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Malformed URL string", e);
    }
    this.url = builder.url;
    this.handleCodeInApp = builder.handleCodeInApp;
    this.dynamicLinkDomain = builder.dynamicLinkDomain;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String url;
    private boolean handleCodeInApp;
    private String dynamicLinkDomain;

    private Builder() { }

    public Builder setUrl(String url) {
      this.url = url;
      return this;
    }

    public Builder setHandleCodeInApp(boolean handleCodeInApp) {
      this.handleCodeInApp = handleCodeInApp;
      return this;
    }

    public Builder setDynamicLinkDomain(String dynamicLinkDomain) {
      this.dynamicLinkDomain = dynamicLinkDomain;
      return this;
    }

    public ActionCodeSettings build() {
      return new ActionCodeSettings(this);
    }
  }

}
