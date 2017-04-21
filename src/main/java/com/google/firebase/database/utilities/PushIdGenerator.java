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

package com.google.firebase.database.utilities;

import java.util.Random;

/** User: greg Date: 5/23/13 Time: 1:27 PM */
public class PushIdGenerator {

  private static final String PUSH_CHARS =
      "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  private static final Random randGen = new Random();
  private static final int[] lastRandChars = new int[12];
  private static long lastPushTime = 0L;

  public static synchronized String generatePushChildName(long now) {
    final boolean duplicateTime = (now == lastPushTime);
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
