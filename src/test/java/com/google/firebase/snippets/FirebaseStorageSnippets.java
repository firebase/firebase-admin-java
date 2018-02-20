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
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Cloud Storage snippets for documentation.
 */
public class FirebaseStorageSnippets {

  public void initializeAppForStorage() throws IOException {
    // [START init_admin_sdk_for_storage]
    FileInputStream serviceAccount = new FileInputStream("path/to/serviceAccountKey.json");

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setStorageBucket("<BUCKET_NAME>.appspot.com")
        .build();
    FirebaseApp.initializeApp(options);

    Bucket bucket = StorageClient.getInstance().bucket();

    // 'bucket' is an object defined in the google-cloud-storage Java library.
    // See http://googlecloudplatform.github.io/google-cloud-java/latest/apidocs/com/google/cloud/storage/Bucket.html
    // for more details.
    // [END init_admin_sdk_for_storage]
    System.out.println("Retrieved bucket: " + bucket.getName());
  }

}
