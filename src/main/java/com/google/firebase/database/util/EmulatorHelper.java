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
  public static final String FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR = "FIREBASE_RTDB_EMULATOR_HOST";

  public static String getEmulatorHostFromEnv() {
    return System.getenv(FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR);
  }

  @VisibleForTesting
  @Nullable
  static String tryToExtractEmulatorUrlFromDbUrl(String suppliedDatabaseUrl) {
    if (Strings.isNullOrEmpty(suppliedDatabaseUrl)) {
      return null;
    }
    ParsedUrl parsedUrl = Utilities.parseUrl(suppliedDatabaseUrl);
    RepoInfo repoInfo = parsedUrl.repoInfo;
    if (repoInfo.isSecure() || repoInfo.host.endsWith(".firebaseio.com") || !suppliedDatabaseUrl
        .contains("ns=")) {
      return null;
    }
    String pathString = parsedUrl.path.isEmpty() ? "/" : parsedUrl.path.toString() + "/";
    return String.format("http://%s%s?ns=%s", repoInfo.host, pathString, repoInfo.namespace);
  }

  public static String overwriteDatabaseUrlWithEmulatorHost(String suppliedDatabaseUrl,
      String emulatorHost) {
    String extractedEmulatorUrl = tryToExtractEmulatorUrlFromDbUrl(suppliedDatabaseUrl);
    if (!Strings.isNullOrEmpty(extractedEmulatorUrl)) {
      return extractedEmulatorUrl;
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
      ParsedUrl parsedUrl = Utilities.parseUrl(suppliedDatabaseUrl);
      namespaceName = parsedUrl.repoInfo.namespace;
      path = parsedUrl.path.isEmpty() ? "/" : parsedUrl.path.toString() + "/";
    }
    // Must format correctly
    return String.format("http://%s%s?ns=%s", emulatorHost, path, namespaceName);
  }
}
