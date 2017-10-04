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

package com.google.firebase;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Provides trampolines into package-private APIs used by components of Firebase
 *
 * <p>Intentionally scarily-named to dissuade people from actually trying to use the class and to
 * make it less likely to appear in code completion.
 *
 * <p>This class will not be compiled into the shipping library and can only be used in tests.
 */
public final class TestOnlyImplFirebaseTrampolines {

  private TestOnlyImplFirebaseTrampolines() {}

  /* FirebaseApp */
  public static void clearInstancesForTest() {
    FirebaseApp.clearInstancesForTest();
  }

  public static String getToken(FirebaseApp app, boolean forceRefresh) {
    GoogleCredentials credentials = app.getOptions().getCredentials();
    try {
      if (forceRefresh) {
        credentials.refresh();
      } else {
        credentials.getRequestMetadata();
      }

      AccessToken token = credentials.getAccessToken();
      return token.getTokenValue();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static GoogleCredentials getCredentials(FirebaseOptions options) {
    return options.getCredentials();
  }

  public static void forceThreadManagerInit(FirebaseApp app) {
    ImplFirebaseTrampolines.submitCallable(app, new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        return null;
      }
    });
  }
}
