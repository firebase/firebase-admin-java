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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.firebase.auth.FirebaseCredential;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.auth.TestOnlyImplFirebaseAuthTrampolines;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.tasks.OnSuccessListener;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.junit.Test;

/** 
 * Tests for {@link FirebaseOptions}.
 */
public class FirebaseOptionsTest {

  private static final String FIREBASE_DB_URL = "https://mock-project.firebaseio.com";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      new FirebaseOptions.Builder()
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setCredential(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .build();

  @Test
  public void createOptionsWithAllValuesSet() throws IOException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder()
            .setDatabaseUrl(FIREBASE_DB_URL)
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    assertEquals(FIREBASE_DB_URL, firebaseOptions.getDatabaseUrl());
    TestOnlyImplFirebaseAuthTrampolines.getCertificate(firebaseOptions.getCredential())
        .addOnSuccessListener(
            new OnSuccessListener<GoogleCredential>() {
              @Override
              public void onSuccess(GoogleCredential googleCredential) {
                assertEquals(
                    ServiceAccount.EDITOR.getEmail(), googleCredential.getServiceAccountId());
                semaphore.release();
              }
            });
    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void createOptionsWithOnlyMandatoryValuesSet() throws IOException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    TestOnlyImplFirebaseAuthTrampolines.getCertificate(firebaseOptions.getCredential())
        .addOnSuccessListener(
            new OnSuccessListener<GoogleCredential>() {
              @Override
              public void onSuccess(GoogleCredential googleCredential) {
                try {
                  assertEquals(
                      GoogleCredential.fromStream(ServiceAccount.EDITOR.asStream())
                          .getServiceAccountId(),
                      googleCredential.getServiceAccountId());
                  semaphore.release();
                } catch (IOException e) {
                  fail();
                }
              }
            });
    TestHelpers.waitFor(semaphore);
  }

  @Test
  public void createOptionsWithServiceAccountSetsProjectId() throws Exception {
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    Task<String> projectId =
        TestOnlyImplFirebaseAuthTrampolines.getProjectId(firebaseOptions.getCredential());
    assertEquals("mock-project-id", Tasks.await(projectId));
  }

  @Test(expected = IllegalArgumentException.class)
  public void createOptionsWithCredentialMissing() {
    new FirebaseOptions.Builder().build();
  }

  @Test
  public void checkToBuilderCreatesNewEquivalentInstance() {
    FirebaseOptions allValuesOptionsCopy = new FirebaseOptions.Builder(ALL_VALUES_OPTIONS).build();
    assertNotSame(ALL_VALUES_OPTIONS, allValuesOptionsCopy);
    assertEquals(ALL_VALUES_OPTIONS, allValuesOptionsCopy);
  }

  @Test
  public void testEquals() throws IOException {
    FirebaseCredential credential = FirebaseCredentials
        .fromCertificate(ServiceAccount.EDITOR.asStream());
    FirebaseOptions options1 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .build();
    FirebaseOptions options2 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .build();
    assertTrue(options1.equals(options2));
  }

  @Test
  public void testNotEquals() throws IOException {
    FirebaseCredential credential = FirebaseCredentials
        .fromCertificate(ServiceAccount.EDITOR.asStream());
    FirebaseOptions options1 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .build();
    FirebaseOptions options2 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .setDatabaseUrl("https://test.firebaseio.com")
            .build();
    assertFalse(options1.equals(options2));
  }

  @Test
  public void testHashCode() throws IOException {
    FirebaseCredential credential = FirebaseCredentials
        .fromCertificate(ServiceAccount.EDITOR.asStream());
    FirebaseOptions options1 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .setDatabaseUrl("https://test.firebaseio.com")
            .build();
    FirebaseOptions options2 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .setDatabaseUrl("https://test.firebaseio.com")
            .build();
    FirebaseOptions options3 =
        new FirebaseOptions.Builder()
            .setCredential(credential)
            .setDatabaseUrl("https://test2.firebaseio.com")
            .build();
    assertEquals(options1.hashCode(), options2.hashCode());
    assertNotEquals(options1.hashCode(), options3.hashCode());
  }
}
