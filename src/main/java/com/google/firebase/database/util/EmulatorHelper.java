package com.google.firebase.database.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.utilities.ParsedUrl;
import com.google.firebase.database.utilities.Utilities;

public final class EmulatorHelper {

  private EmulatorHelper() {
  }

  @VisibleForTesting
  public static final String FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR = "FIREBASE_RTDB_EMULATOR_HOST";

  private static String getEmulatorHostFromEnv() {
    return System.getenv(FIREBASE_RTDB_EMULATOR_HOST_ENV_VAR);
  }

  public static ParsedUrl parsedUrlForEmulator(String dbName, String emulatorUrl) {
    RepoInfo repoInfo = new RepoInfo();
    repoInfo.host = emulatorUrl;
    repoInfo.namespace = dbName;
    repoInfo.secure = false;
    ParsedUrl parsedUrl = new ParsedUrl();
    parsedUrl.repoInfo = repoInfo;
    parsedUrl.path = new Path("");
    return parsedUrl;
  }

  @VisibleForTesting
  static String extractEmulatorUrlFromDbUrl(String suppliedDatabaseUrl) {
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

  public static String getEmulatorUrl(String suppliedDatabaseUrl) {
    String extractedEmulatorUrl = extractEmulatorUrlFromDbUrl(suppliedDatabaseUrl);
    if (!Strings.isNullOrEmpty(extractedEmulatorUrl)) {
      return extractedEmulatorUrl;
    }
    String emulatorHostFromEnv = getEmulatorHostFromEnv();
    if (Strings.isNullOrEmpty(emulatorHostFromEnv)) {
      return null;
    }
    if (emulatorHostFromEnv.contains("http:") || emulatorHostFromEnv.contains("?ns=")) {
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
    return String.format("http://%s%s?ns=%s", emulatorHostFromEnv, path, namespaceName);
  }
}
