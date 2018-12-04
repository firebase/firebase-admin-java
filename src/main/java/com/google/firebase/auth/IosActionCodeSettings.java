package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.Map;

/**
 * Defines the iOS related settings corresponding to the action code if it is to be handled in an
 * iOS app.
 */
public final class IosActionCodeSettings {

  private final String bundleId;

  /**
   * Creates a new instance of {@code IosActionCodeSettings}.
   *
   * @param bundleId The required iOS bundle ID of the app where the link should be handled if the
   *     application is already installed on the device.
   */
  public IosActionCodeSettings(@NonNull String bundleId) {
    checkArgument(!Strings.isNullOrEmpty(bundleId), "Bundle ID must not be null or empty");
    this.bundleId = bundleId;
  }

  Map<String, Object> getProperties() {
    return ImmutableMap.<String, Object>of("bundleId", bundleId);
  }
}
