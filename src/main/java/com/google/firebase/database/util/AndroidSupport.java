package com.google.firebase.database.util;

public class AndroidSupport {

  private static final boolean IS_ANDROID = checkAndroid();

  private static boolean checkAndroid() {
    try {
      Class contextClass = Class.forName("android.app.Activity");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public static boolean isAndroid() {
    return IS_ANDROID;
  }
}
