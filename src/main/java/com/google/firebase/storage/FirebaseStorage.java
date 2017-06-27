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

package com.google.firebase.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseOAuthCredentials;
import com.google.firebase.internal.FirebaseService;

/**
 * FirebaseStorage is a service that supports uploading and downloading large objects to Google
 * Cloud Storage. You can specify a cloud storage bucket via
 * {@link com.google.firebase.FirebaseOptions}, and then get a reference to it by calling
 * {@link FirebaseStorage#getBucket()}. Or if you have the bucket name at runtime you can
 * directly call {@link FirebaseStorage#getBucket(String)}, with the bucket name as an argument.
 *
 * <p>This service requires Google Cloud Storage libraries for Java. Make sure the artifact
 * google-cloud-storage is in classpath along with its transitive dependencies.
 */
public class FirebaseStorage {

  private final FirebaseApp app;
  private final Storage storage;

  private FirebaseStorage(FirebaseApp app) {
    this.app = checkNotNull(app, "FirebaseApp must not be null");
    this.storage = StorageOptions.newBuilder()
        .setCredentials(new FirebaseOAuthCredentials(app))
        .build()
        .getService();
  }

  public static FirebaseStorage getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  public static FirebaseStorage getInstance(FirebaseApp app) {
    FirebaseStorageService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        FirebaseStorageService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseStorageService(app));
    }
    return service.getInstance();
  }

  /**
   * Returns the default cloud storage bucket associated with the current app. This is the bucket
   * configured via {@link com.google.firebase.FirebaseOptions} when initializing the app. If
   * no bucket was configured via options, this method throws an exception.
   *
   * @return a cloud storage Bucket instance, or null if the configured bucket does not exist.
   * @throws IllegalArgumentException If no bucket is configured via <code>FirebaseOptions</code>.
   */
  public Bucket getBucket() {
    return getBucket(app.getOptions().getStorageBucket());
  }

  /**
   * Returns a cloud storage Bucket instance for the specified bucket name.
   *
   * @param name a non-null, non-empty bucket name.
   * @return a cloud storage Bucket instance, or null of the specified bucket does not exist.
   * @throws IllegalArgumentException If the bucket name is null or empty.
   */
  public Bucket getBucket(String name) {
    checkArgument(!Strings.isNullOrEmpty(name),
        "Bucket name must not be null or empty. If you're trying to access the default "
            + "storage bucket, set the bucket name via FirebaseOptions.");
    return storage.get(name);
  }

  private static final String SERVICE_ID = FirebaseStorage.class.getName();

  private static class FirebaseStorageService extends FirebaseService<FirebaseStorage> {

    FirebaseStorageService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseStorage(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of FirebaseAuth
      // will now fail because calls to getCredential() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }

}
