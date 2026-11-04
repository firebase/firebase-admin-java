/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.internal.FirebaseProcessEnvironment;
import java.util.regex.Pattern;

public class Utils {
  @VisibleForTesting
  public static final String AUTH_EMULATOR_HOST = "FIREBASE_AUTH_EMULATOR_HOST";

  private static final Pattern SAFE_URL_SEGMENT_PATTERN =
      Pattern.compile("^[a-zA-Z0-9\\-_.~]+$");

  public static boolean isEmulatorMode() {
    return !Strings.isNullOrEmpty(getEmulatorHost());
  }

  public static String getEmulatorHost() {
    return FirebaseProcessEnvironment.getenv(AUTH_EMULATOR_HOST);
  }

  public static boolean isSafeUrlSegment(String segment) {
    return !Strings.isNullOrEmpty(segment)
        && !segment.endsWith(".")
        && SAFE_URL_SEGMENT_PATTERN.matcher(segment).matches();
  }

  public static void checkTenantId(String tenantId) {
    checkArgument(!Strings.isNullOrEmpty(tenantId), "Tenant ID must not be null or empty.");
    checkArgument(isSafeUrlSegment(tenantId), "Invalid tenant ID: " + tenantId);
  }

}
