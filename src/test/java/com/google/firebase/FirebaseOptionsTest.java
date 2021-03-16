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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.junit.Test;

/** 
 * Tests for {@link FirebaseOptions}.
 */
public class FirebaseOptionsTest {

  private static final String FIREBASE_DB_URL = "https://mock-project.firebaseio.com";
  private static final String FIREBASE_STORAGE_BUCKET = "mock-storage-bucket";
  private static final String FIREBASE_PROJECT_ID = "explicit-project-id";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      FirebaseOptions.builder()
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setStorageBucket(FIREBASE_STORAGE_BUCKET)
          .setProjectId(FIREBASE_PROJECT_ID)
          .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .build();

  private static final ThreadManager MOCK_THREAD_MANAGER = new ThreadManager() {
    @Override
    protected ExecutorService getExecutor(FirebaseApp app) {
      return null;
    }

    @Override
    protected void releaseExecutor(FirebaseApp app, ExecutorService executor) {
    }

    @Override
    protected ThreadFactory getThreadFactory() {
      return null;
    }
  };

  @Test
  public void createOptionsWithAllValuesSet() throws IOException {
    GsonFactory jsonFactory = new GsonFactory();
    NetHttpTransport httpTransport = new NetHttpTransport();
    FirestoreOptions firestoreOptions = FirestoreOptions.newBuilder().build();
    FirebaseOptions firebaseOptions =
        FirebaseOptions.builder()
            .setDatabaseUrl(FIREBASE_DB_URL)
            .setStorageBucket(FIREBASE_STORAGE_BUCKET)
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .setProjectId(FIREBASE_PROJECT_ID)
            .setJsonFactory(jsonFactory)
            .setHttpTransport(httpTransport)
            .setThreadManager(MOCK_THREAD_MANAGER)
            .setConnectTimeout(30000)
            .setReadTimeout(60000)
            .setFirestoreOptions(firestoreOptions)
            .build();
    assertEquals(FIREBASE_DB_URL, firebaseOptions.getDatabaseUrl());
    assertEquals(FIREBASE_STORAGE_BUCKET, firebaseOptions.getStorageBucket());
    assertEquals(FIREBASE_PROJECT_ID, firebaseOptions.getProjectId());
    assertSame(jsonFactory, firebaseOptions.getJsonFactory());
    assertSame(httpTransport, firebaseOptions.getHttpTransport());
    assertSame(MOCK_THREAD_MANAGER, firebaseOptions.getThreadManager());
    assertEquals(30000, firebaseOptions.getConnectTimeout());
    assertEquals(60000, firebaseOptions.getReadTimeout());
    assertSame(firestoreOptions, firebaseOptions.getFirestoreOptions());

    GoogleCredentials credentials = firebaseOptions.getCredentials();
    assertNotNull(credentials);
    assertTrue(credentials instanceof ServiceAccountCredentials);
    assertEquals(
        ServiceAccount.EDITOR.getEmail(),
        ((ServiceAccountCredentials) credentials).getClientEmail());
  }

  @Test
  public void createOptionsWithOnlyMandatoryValuesSet() throws IOException {
    FirebaseOptions firebaseOptions =
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .build();
    assertNotNull(firebaseOptions.getJsonFactory());
    assertNotNull(firebaseOptions.getHttpTransport());
    assertNotNull(firebaseOptions.getThreadManager());
    assertNull(firebaseOptions.getDatabaseUrl());
    assertNull(firebaseOptions.getStorageBucket());
    assertEquals(0, firebaseOptions.getConnectTimeout());
    assertEquals(0, firebaseOptions.getReadTimeout());

    GoogleCredentials credentials = firebaseOptions.getCredentials();
    assertNotNull(credentials);
    assertTrue(credentials instanceof ServiceAccountCredentials);
    assertEquals(
        ServiceAccount.EDITOR.getEmail(),
        ((ServiceAccountCredentials) credentials).getClientEmail());
    assertNull(firebaseOptions.getFirestoreOptions());
  }

  @Test
  public void createOptionsWithCustomFirebaseCredential() {
    FirebaseOptions firebaseOptions =
        FirebaseOptions.builder()
            .setCredentials(new GoogleCredentials() {
              @Override
              public AccessToken refreshAccessToken() {
                return null;
              }
            })
            .build();

    assertNotNull(firebaseOptions.getJsonFactory());
    assertNotNull(firebaseOptions.getHttpTransport());
    assertNull(firebaseOptions.getDatabaseUrl());
    assertNull(firebaseOptions.getStorageBucket());

    GoogleCredentials credentials = firebaseOptions.getCredentials();
    assertNotNull(credentials);
  }

  @Test(expected = NullPointerException.class)
  public void createOptionsWithCredentialMissing() {
    FirebaseOptions.builder().build().getCredentials();
  }

  @Test(expected = NullPointerException.class)
  public void createOptionsWithNullCredentials() {
    FirebaseOptions.builder().setCredentials((GoogleCredentials) null).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void createOptionsWithStorageBucketUrl() throws IOException {
    FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setStorageBucket("gs://mock-storage-bucket")
        .build();
  }

  @Test(expected = NullPointerException.class)
  public void createOptionsWithNullThreadManager() {
    FirebaseOptions.builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .setThreadManager(null)
        .build();
  }

  @Test
  public void checkToBuilderCreatesNewEquivalentInstance() {
    FirebaseOptions allValuesOptionsCopy = ALL_VALUES_OPTIONS.toBuilder().build();
    assertNotSame(ALL_VALUES_OPTIONS, allValuesOptionsCopy);
    assertEquals(ALL_VALUES_OPTIONS.getCredentials(), allValuesOptionsCopy.getCredentials());
    assertEquals(ALL_VALUES_OPTIONS.getDatabaseUrl(), allValuesOptionsCopy.getDatabaseUrl());
    assertEquals(ALL_VALUES_OPTIONS.getProjectId(), allValuesOptionsCopy.getProjectId());
    assertEquals(ALL_VALUES_OPTIONS.getJsonFactory(), allValuesOptionsCopy.getJsonFactory());
    assertEquals(ALL_VALUES_OPTIONS.getHttpTransport(), allValuesOptionsCopy.getHttpTransport());
    assertEquals(ALL_VALUES_OPTIONS.getThreadManager(), allValuesOptionsCopy.getThreadManager());
    assertEquals(ALL_VALUES_OPTIONS.getConnectTimeout(), allValuesOptionsCopy.getConnectTimeout());
    assertEquals(ALL_VALUES_OPTIONS.getReadTimeout(), allValuesOptionsCopy.getReadTimeout());
    assertSame(ALL_VALUES_OPTIONS.getFirestoreOptions(),
        allValuesOptionsCopy.getFirestoreOptions());
  }

  @Test(expected = IllegalArgumentException.class)
  public void createOptionsWithInvalidConnectTimeout() {
    FirebaseOptions.builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .setConnectTimeout(-1)
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void createOptionsWithInvalidReadTimeout() {
    FirebaseOptions.builder()
        .setCredentials(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
        .setReadTimeout(-1)
        .build();
  }

  @Test
  public void testNotEquals() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream());
    FirebaseOptions options1 =
        FirebaseOptions.builder()
            .setCredentials(credentials)
            .build();
    FirebaseOptions options2 =
        FirebaseOptions.builder()
            .setCredentials(credentials)
            .setDatabaseUrl("https://test.firebaseio.com")
            .build();
    assertNotEquals(options1, options2);
  }
}
