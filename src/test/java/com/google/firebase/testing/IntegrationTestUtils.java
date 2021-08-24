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

package com.google.firebase.testing;

import com.google.api.client.json.GenericJson;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.ApiClientUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class IntegrationTestUtils {

  private static final String IT_SERVICE_ACCOUNT_PATH = "integration_cert.json";
  private static final String IT_API_KEY_PATH = "integration_apikey.txt";

  private static GenericJson serviceAccount;
  private static String apiKey;
  private static FirebaseApp masterApp;

  private static synchronized GenericJson ensureServiceAccount() {
    if (serviceAccount == null) {
      try (InputStream stream = new FileInputStream(IT_SERVICE_ACCOUNT_PATH)) {
        serviceAccount = ApiClientUtils.getDefaultJsonFactory()
          .fromInputStream(stream, GenericJson.class);
      } catch (IOException e) {
        String msg = String.format("Failed to read service account certificate from %s. "
            + "Integration tests require a service account credential obtained from a Firebase "
            + "project. See CONTRIBUTING.md for more details.", IT_SERVICE_ACCOUNT_PATH);
        throw new RuntimeException(msg, e);
      }
    }
    return serviceAccount;
  }

  public static InputStream getServiceAccountCertificate() {
    return new ByteArrayInputStream(ensureServiceAccount().toString().getBytes());
  }

  public static String getProjectId() {
    return ensureServiceAccount().get("project_id").toString();
  }

  public static String getDatabaseUrl() {
    return "https://" + getProjectId() + ".firebaseio.com";
  }

  public static String getStorageBucket() {
    return getProjectId() + ".appspot.com";
  }

  public static synchronized String getApiKey() {
    if (apiKey == null) {
      try (InputStream stream = new FileInputStream(IT_API_KEY_PATH)) {
        apiKey = CharStreams.toString(new InputStreamReader(stream)).trim();
      } catch (IOException e) {
        String msg = String.format("Failed to read API key from %s. "
            + "Integration tests require an API key obtained from a Firebase "
            + "project. See CONTRIBUTING.md for more details.", IT_API_KEY_PATH);
        throw new RuntimeException(msg, e);
      }
    }
    return apiKey;
  }

  /**
   * Initializes the default FirebaseApp for integration testing (if not already initialized), and
   * returns it. Integration tests that interact with the default FirebaseApp should call this
   * method to obtain the app instance. This method ensures that all integration tests get the
   * same FirebaseApp instance, instead of initializing an app per test.
   *
   * @return the default FirebaseApp instance
   */
  public static synchronized FirebaseApp ensureDefaultApp() {
    if (masterApp == null) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setDatabaseUrl(getDatabaseUrl())
              .setStorageBucket(getStorageBucket())
              .setCredentials(TestUtils.getCertCredential(getServiceAccountCertificate()))
              .setFirestoreOptions(FirestoreOptions.newBuilder()
                  .setCredentials(TestUtils.getCertCredential(getServiceAccountCertificate()))
                  .build())
              .build();
      masterApp = FirebaseApp.initializeApp(options);
    }
    return masterApp;
  }

  public static FirebaseApp initApp(String name) {
    FirebaseOptions options =
        FirebaseOptions.builder()
            .setDatabaseUrl(getDatabaseUrl())
            .setCredentials(TestUtils.getCertCredential(getServiceAccountCertificate()))
            .build();
    return FirebaseApp.initializeApp(options, name);
  }

  public static DatabaseReference getRandomNode(FirebaseApp app) {
    return getRandomNode(app, 1).get(0);
  }

  public static List<DatabaseReference> getRandomNode(FirebaseApp app, int count) {
    FirebaseDatabase database = FirebaseDatabase.getInstance(app);
    ImmutableList.Builder<DatabaseReference> builder = ImmutableList.builder();
    String name = null;
    for (int i = 0; i < count; i++) {
      if (name == null) {
        DatabaseReference ref = database.getReference().push();
        builder.add(ref);
        name = ref.getKey();
      } else {
        builder.add(database.getReference().child(name));
      }
    }
    return builder.build();
  }
}
