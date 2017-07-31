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

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Strings;
import com.google.common.net.UrlEscapers;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseCloudCredentials;
import com.google.firebase.internal.FirebaseService;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * StorageClient provides access to Google Cloud Storage APIs. You can specify a default cloud
 * storage bucket via {@link com.google.firebase.FirebaseOptions}, and then get a reference to this
 * default bucket by calling {@link StorageClient#getBucket()}. Or you can get a reference to a
 * specific bucket at any time by calling {@link StorageClient#getBucket(String)}.
 *
 * <p>This class requires Google Cloud Storage libraries for Java. Make sure the artifact
 * google-cloud-storage is in the classpath along with its transitive dependencies.
 */
public class StorageClient {

  // Key used tp store previously-created download tokens in a Blob's metadata.
  // The same key is used by the client SDKs and the Firebase console to store and manage
  // download tokens for GCS objects.
  private static final String DOWNLOAD_TOKENS_METADATA_KEY = "firebaseStorageDownloadTokens";

  private final FirebaseApp app;
  private final Storage storage;

  private StorageClient(FirebaseApp app) {
    this.app = checkNotNull(app, "FirebaseApp must not be null");
    this.storage = StorageOptions.newBuilder()
        .setCredentials(new FirebaseCloudCredentials(app))
        .build()
        .getService();
  }

  public static StorageClient getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  public static StorageClient getInstance(FirebaseApp app) {
    StorageClientService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
        StorageClientService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new StorageClientService(app));
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

  /**
   * Returns a long-lived download URL with a revokable token. This can be used to share a file
   * with others, but can be revoked by a developer in the Firebase console if desired.
   *
   * @param blob The blob to generate a download URL for.
   * @return a URL string.
   */
  public String getDownloadUrl(Blob blob) {
    checkNotNull(blob, "Blob must not be null");
    Map<String, String> metadata = blob.getMetadata();
    if (metadata == null) {
      metadata = new HashMap<>();
    }
    String downloadTokens = metadata.get(DOWNLOAD_TOKENS_METADATA_KEY);
    if (Strings.isNullOrEmpty(downloadTokens)) {
      downloadTokens = UUID.randomUUID().toString();
      metadata.put(DOWNLOAD_TOKENS_METADATA_KEY, downloadTokens);
      blob.toBuilder().setMetadata(metadata).build().update();
    }

    String token = downloadTokens.split(",")[0];
    return String.format("https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
        blob.getBucket(), blob.getName(), UrlEscapers.urlFormParameterEscaper().escape(token));
  }

  private static final String SERVICE_ID = StorageClient.class.getName();

  private static class StorageClientService extends FirebaseService<StorageClient> {

    StorageClientService(FirebaseApp app) {
      super(SERVICE_ID, new StorageClient(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of StorageClient
      // will now fail because calls to getOptions() and getToken() will hit FirebaseApp,
      // which will throw once the app is deleted.
    }
  }

}
