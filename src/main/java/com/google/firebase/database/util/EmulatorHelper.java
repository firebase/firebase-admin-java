/*
 * Copyright 2019 Google Inc.
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

package com.google.firebase.database.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.utilities.ParsedUrl;
import com.google.firebase.database.utilities.Utilities;

public final class EmulatorHelper {

  private EmulatorHelper() {
  }

  @VisibleForTesting
  public static final String FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR =
      "FIREBASE_DATABASE_EMULATOR_HOST";

  public static String getEmulatorHostFromEnv() {
    return System.getenv(FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR);
  }

  @VisibleForTesting
  @Nullable
  static boolean isEmulatorUrl(String databaseUrl) {
    if (Strings.isNullOrEmpty(databaseUrl)) {
      return false;
    }
    RepoInfo repoInfo = Utilities.parseUrl(databaseUrl).repoInfo;
    return !repoInfo.host.endsWith(".firebaseio.com") && databaseUrl.contains("ns=");
  }

  @Nullable
  public static String getEmulatorUrl(String suppliedDatabaseUrl, String emulatorHost) {
    if (isEmulatorUrl(suppliedDatabaseUrl)) {
      return suppliedDatabaseUrl;
    }
    if (Strings.isNullOrEmpty(emulatorHost)) {
      return null;
    }
    if (emulatorHost.contains("http:") || emulatorHost.contains("?ns=")) {
      throw new IllegalArgumentException(
          "emulator host declared in environment variable must be of the format \"host:port\"");
    }
    String namespaceName = "default";
    String path = "/";
    if (!Strings.isNullOrEmpty(suppliedDatabaseUrl)) {
      ParsedUrl parsedDbUrl = Utilities.parseUrl(suppliedDatabaseUrl);
      namespaceName = parsedDbUrl.repoInfo.namespace;
      path = parsedDbUrl.path.isEmpty() ? "/" : parsedDbUrl.path.toString() + "/";
    }
    // Must format correctly
    return String.format("http://%s%s?ns=%s", emulatorHost, path, namespaceName);
  }
}
