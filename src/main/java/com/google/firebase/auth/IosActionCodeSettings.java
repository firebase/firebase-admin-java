package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;

public final class IosActionCodeSettings {

  @Key("bundleId")
  private final String bundleId;

  public IosActionCodeSettings(String bundleId) {
    checkArgument(!Strings.isNullOrEmpty(bundleId), "Bundle ID must not be null or empty");
    this.bundleId = bundleId;
  }
}
