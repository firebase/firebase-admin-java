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

package com.google.firebase.cloud;

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
 * StorageWrapper provides access to Google Cloud Storage APIs. You can specify a cloud storage
 * bucket via {@link com.google.firebase.FirebaseOptions}, and then get a reference to it by calling
 * {@link StorageWrapper#getBucket()}. Or if you know the bucket name at runtime you can
 * directly call {@link StorageWrapper#getBucket(String)}.
 *
 * <p>This class requires Google Cloud Storage libraries for Java. Make sure the artifact
 * google-cloud-storage is in classpath along with its transitive dependencies.
 */
public class StorageWrapper {

  private final FirebaseApp app;
  private final Storage storage;

  private StorageWrapper(FirebaseApp app) {
    this.app = checkNotNull(app, "FirebaseApp must not be null");
    this.storage = StorageOptions.newBuilder()
        .setCredentials(new FirebaseOAuthCredentials(app))
        .build()
        .getService();
  }

  public static StorageWrapper getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  public static StorageWrapper getInstance(FirebaseApp app) {
    StorageWrapperService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        StorageWrapperService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new StorageWrapperService(app));
    }
    return service.getInstance();
  }

  /**
   * Returns the default cloud storage bucket associated with the current app. This is the bucket
   * configured via {@link com.google.firebase.FirebaseOptions} when initializing the app. If
   * no bucket was configured via options, this method throws an exception.
   *
   * @return a cloud storage Bucket instance, or null if the configured bucket does not exist.
   * @throws IllegalArgumentException If no bucket is configured via <code>FirebaseOptions</code>,
   *     or if the bucket does not exist.
   */
  public Bucket getBucket() {
    return getBucket(app.getOptions().getStorageBucket());
  }

  /**
   * Returns a cloud storage Bucket instance for the specified bucket name.
   *
   * @param name a non-null, non-empty bucket name.
   * @return a cloud storage Bucket instance, or null if the specified bucket does not exist.
   * @throws IllegalArgumentException If the bucket name is null, empty, or if the specified
   *     bucket does not exist.
   */
  public Bucket getBucket(String name) {
    checkArgument(!Strings.isNullOrEmpty(name),
        "Bucket name not specified. Specify the bucket name via the storageBucket "
            + "option when initializing the app, or specify the bucket name explicitly when "
            + "calling the getBucket() method.");
    Bucket bucket = storage.get(name);
    checkArgument(bucket != null, "Bucket " + name + " does not exist.");
    return bucket;
  }

  private static final String SERVICE_ID = StorageWrapper.class.getName();

  private static class StorageWrapperService extends FirebaseService<StorageWrapper> {

    StorageWrapperService(FirebaseApp app) {
      super(SERVICE_ID, new StorageWrapper(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of StorageWrapper
      // will now fail because calls to getOptions() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }

}
