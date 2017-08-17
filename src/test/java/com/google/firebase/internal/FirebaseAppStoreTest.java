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

package com.google.firebase.internal;

import static org.junit.Assert.assertTrue;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class FirebaseAppStoreTest {

  private static final String FIREBASE_DB_URL = "https://mock-project.firebaseio.com";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      new FirebaseOptions.Builder()
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .build();

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  @Test
  public void compatibleAppInitializedInNextRunOk() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
  }

  @Test
  public void incompatibleAppInitializedDoesntThrow() throws IOException {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp.initializeApp(options, name);
  }

  @Test
  public void incompatibleDefaultAppInitializedDoesntThrow() throws IOException {
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp.initializeApp(options);
  }

  @Test
  public void persistenceDisabled() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseAppStore appStore = FirebaseAppStore.getInstance();
    assertTrue(!appStore.getAllPersistedAppNames().contains(name));
  }
}
