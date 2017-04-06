package com.google.firebase.database.utilities;

import java.util.Random;

/**
 * User: greg Date: 5/23/13 Time: 1:27 PM
 */
public class PushIdGenerator {

  private static final String PUSH_CHARS =
      "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  private static final Random randGen = new Random();
  private static final int[] lastRandChars = new int[12];
  private static long lastPushTime = 0L;

  public static synchronized String generatePushChildName(long now) {
    boolean duplicateTime = (now == lastPushTime);
    lastPushTime = now;

    char[] timeStampChars = new char[8];
    StringBuilder result = new StringBuilder(20);
    for (int i = 7; i >= 0; i--) {
      timeStampChars[i] = PUSH_CHARS.charAt((int) (now % 64));
      now = now / 64;
    }
    assert (now == 0);

    result.append(timeStampChars);

    if (!duplicateTime) {
      for (int i = 0; i < 12; i++) {
        lastRandChars[i] = randGen.nextInt(64);
      }
    } else {
      incrementArray();
    }
    for (int i = 0; i < 12; i++) {
      result.append(PUSH_CHARS.charAt(lastRandChars[i]));
    }
    assert (result.length() == 20);
    return result.toString();
  }

  private static void incrementArray() {
    for (int i = 11; i >= 0; i--) {
      if (lastRandChars[i] != 63) {
        lastRandChars[i] = lastRandChars[i] + 1;
        return;
      }
      lastRandChars[i] = 0;
    }
  }
}
