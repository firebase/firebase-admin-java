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
