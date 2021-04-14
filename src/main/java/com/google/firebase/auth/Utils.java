package com.google.firebase.auth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

public class Utils {
  @VisibleForTesting
  public static final String AUTH_EMULATOR_HOST = "FIREBASE_AUTH_EMULATOR_HOST";

  public static boolean isEmulatorMode() {
    return !Strings.isNullOrEmpty(
        System.getenv(AUTH_EMULATOR_HOST));
  }

  public static String getEmulatorHost() {
    return System.getenv(AUTH_EMULATOR_HOST);
  }

}
