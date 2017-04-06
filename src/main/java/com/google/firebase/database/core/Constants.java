package com.google.firebase.database.core;

import com.google.firebase.database.snapshot.ChildKey;

/**
 * User: greg Date: 5/16/13 Time: 3:52 PM
 */
public class Constants {

  public static final ChildKey DOT_INFO = ChildKey.fromString(".info");
  public static final ChildKey DOT_INFO_SERVERTIME_OFFSET = ChildKey.fromString("serverTimeOffset");
  public static final ChildKey DOT_INFO_AUTHENTICATED = ChildKey.fromString("authenticated");
  public static final ChildKey DOT_INFO_CONNECTED = ChildKey.fromString("connected");

  public static final String WIRE_PROTOCOL_VERSION = "5";
}
