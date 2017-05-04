/*
 * Copyright 2017 Google Inc.
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

package com.google.firebase.database.connection;

import java.net.URI;

public class HostInfo {

  private static final String VERSION_PARAM = "v";
  private static final String LAST_SESSION_ID_PARAM = "ls";

  private final String host;
  private final String namespace;
  private final boolean secure;

  public HostInfo(String host, String namespace, boolean secure) {
    this.host = host;
    this.namespace = namespace;
    this.secure = secure;
  }

  public static URI getConnectionUrl(
      String host, boolean secure, String namespace, String optLastSessionId) {
    String scheme = secure ? "wss" : "ws";
    String url =
        scheme
            + "://"
            + host
            + "/.ws?ns="
            + namespace
            + "&"
            + VERSION_PARAM
            + "="
            + Constants.WIRE_PROTOCOL_VERSION;
    if (optLastSessionId != null) {
      url += "&" + LAST_SESSION_ID_PARAM + "=" + optLastSessionId;
    }
    return URI.create(url);
  }

  @Override
  public String toString() {
    return "http" + (secure ? "s" : "") + "://" + host;
  }

  public String getHost() {
    return this.host;
  }

  public String getNamespace() {
    return this.namespace;
  }

  public boolean isSecure() {
    return secure;
  }
}
