/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.snippets;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * FirebaseApp initialization snippets for documentation.
 */
public class FirebaseAppSnippets {

  public void initializeWithServiceAccount() throws IOException {
    // [START initialize_sdk_with_service_account]
    FileInputStream serviceAccount = new FileInputStream("path/to/serviceAccountKey.json");

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setDatabaseUrl("https://<DATABASE_NAME>.firebaseio.com/")
        .build();

    FirebaseApp.initializeApp(options);
    // [END initialize_sdk_with_service_account]
  }

  public void initializeWithDefaultCredentials() throws IOException {
    // [START initialize_sdk_with_application_default]
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .setDatabaseUrl("https://<DATABASE_NAME>.firebaseio.com/")
        .build();

    FirebaseApp.initializeApp(options);
    // [END initialize_sdk_with_application_default]
  }

  public void initializeWithRefreshToken() throws IOException {
    // [START initialize_sdk_with_refresh_token]
    FileInputStream refreshToken = new FileInputStream("path/to/refreshToken.json");

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(refreshToken))
        .setDatabaseUrl("https://<DATABASE_NAME>.firebaseio.com/")
        .build();

    FirebaseApp.initializeApp(options);
    // [END initialize_sdk_with_refresh_token]
  }

  public void initializeWithDefaultConfig() {
    // [START initialize_sdk_with_default_config]
    FirebaseApp.initializeApp();
    // [END initialize_sdk_with_default_config]
  }

  public void initializeDefaultApp() throws IOException {
    FirebaseOptions defaultOptions = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build();

    // [START access_services_default]
    // Initialize the default app
    FirebaseApp defaultApp = FirebaseApp.initializeApp(defaultOptions);

    System.out.println(defaultApp.getName());  // "[DEFAULT]"

    // Retrieve services by passing the defaultApp variable...
    FirebaseAuth defaultAuth = FirebaseAuth.getInstance(defaultApp);
    FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance(defaultApp);

    // ... or use the equivalent shorthand notation
    defaultAuth = FirebaseAuth.getInstance();
    defaultDatabase = FirebaseDatabase.getInstance();
    // [END access_services_default]
  }

  public void initializeCustomApp() throws Exception {
    FirebaseOptions defaultOptions = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build();
    FirebaseOptions otherAppConfig = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build();

    // [START access_services_nondefault]
    // Initialize the default app
    FirebaseApp defaultApp = FirebaseApp.initializeApp(defaultOptions);

    // Initialize another app with a different config
    FirebaseApp otherApp = FirebaseApp.initializeApp(otherAppConfig, "other");

    System.out.println(defaultApp.getName());  // "[DEFAULT]"
    System.out.println(otherApp.getName());    // "other"

    // Use the shorthand notation to retrieve the default app's services
    FirebaseAuth defaultAuth = FirebaseAuth.getInstance();
    FirebaseDatabase defaultDatabase = FirebaseDatabase.getInstance();

    // Use the otherApp variable to retrieve the other app's services
    FirebaseAuth otherAuth = FirebaseAuth.getInstance(otherApp);
    FirebaseDatabase otherDatabase = FirebaseDatabase.getInstance(otherApp);
    // [END access_services_nondefault]
  }

  public void initializeWithServiceAccountId() {
    // [START initialize_sdk_with_service_account_id]
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setServiceAccountId("my-client-id@my-project-id.iam.gserviceaccount.com")
        .build();
    FirebaseApp.initializeApp(options);
    // [END initialize_sdk_with_service_account_id]
  }
}
