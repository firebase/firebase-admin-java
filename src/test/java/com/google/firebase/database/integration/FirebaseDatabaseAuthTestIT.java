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

package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.testing.IntegrationTestUtils;
import com.google.firebase.testing.IntegrationTestUtils.AppHttpClient;
import com.google.firebase.testing.IntegrationTestUtils.ResponseInfo;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseDatabaseAuthTestIT {
  
  private static FirebaseApp masterApp;
  
  @BeforeClass
  public static void setUpClass() throws IOException {    
    masterApp = IntegrationTestUtils.ensureDefaultApp();
    setDatabaseRules();
  }

  @Before
  public void prepareApp() {
    TestHelpers.wrapForErrorHandling(masterApp);
  }

  @After
  public void checkAndCleanupApp() {
    TestHelpers.assertAndUnwrapErrorHandlers(masterApp);
  }

  @Test
  public void testAuthWithValidCertificateCredential() throws InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertWriteSucceeds(db.getReference());
    assertReadSucceeds(db.getReference());
  }
  
  @Test
  public void testAuthWithInvalidCertificateCredential() throws InterruptedException, IOException {
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setDatabaseUrl(IntegrationTestUtils.getDatabaseUrl())
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.NONE.asStream()))
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestNoRole");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    // TODO: Ideally, we would find a way to verify the correct log output.
    assertWriteTimeout(db.getReference());
  }
  
  @Test
  public void testDatabaseAuthVariablesAuthorization() throws InterruptedException {
    Map<String, Object> authVariableOverrides = ImmutableMap.<String, Object>of(
        "uid", "test",
        "custom", "secret"
    );
    FirebaseOptions options =
        new FirebaseOptions.Builder(masterApp.getOptions())
            .setDatabaseAuthVariableOverride(authVariableOverrides)
            .build();
    FirebaseApp testUidApp = FirebaseApp.initializeApp(options, "testGetAppWithUid");
    FirebaseDatabase masterDb = FirebaseDatabase.getInstance(masterApp);
    FirebaseDatabase testAuthOverridesDb = FirebaseDatabase.getInstance(testUidApp);

    assertWriteSucceeds(masterDb.getReference());

    // "test" UID can only read/write to /test-uid-only and /test-custom-field-only locations.
    assertWriteFails(testAuthOverridesDb.getReference());
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-uid-only"));
    assertReadSucceeds(testAuthOverridesDb.getReference("test-uid-only"));
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-custom-field-only"));
    assertReadSucceeds(testAuthOverridesDb.getReference("test-custom-field-only"));
  }
  
  @Test
  public void testDatabaseAuthVariablesNoAuthorization() throws InterruptedException {
    FirebaseOptions options =
        new FirebaseOptions.Builder(masterApp.getOptions())
            .setDatabaseAuthVariableOverride(null)
            .build();
    FirebaseApp testUidApp =
        FirebaseApp.initializeApp(options, "testServiceAccountDatabaseWithNoAuth");

    FirebaseDatabase masterDb = FirebaseDatabase.getInstance(masterApp);
    FirebaseDatabase testAuthOverridesDb = FirebaseDatabase.getInstance(testUidApp);

    assertWriteSucceeds(masterDb.getReference());

    assertWriteFails(testAuthOverridesDb.getReference("test-uid-only"));
    assertReadFails(testAuthOverridesDb.getReference("test-uid-only"));
    assertWriteFails(testAuthOverridesDb.getReference("test-custom-field-only"));
    assertReadFails(testAuthOverridesDb.getReference("test-custom-field-only"));
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-noauth-only"));    
  }
  
  private static void assertWriteSucceeds(DatabaseReference ref) throws InterruptedException {
    doWrite(ref, /*shouldSucceed=*/ true, /*shouldTimeout=*/ false);
  }
  
  private static void assertWriteFails(DatabaseReference ref) throws InterruptedException {
    doWrite(ref, /*shouldSucceed=*/ false, /*shouldTimeout=*/ false);
  }
  
  private static void assertWriteTimeout(DatabaseReference ref) throws InterruptedException {
    doWrite(ref, /*shouldSucceed=*/ false, /*shouldTimeout=*/ true);
  }
  
  private static void assertReadSucceeds(DatabaseReference ref) throws InterruptedException {
    doRead(ref, /*shouldSucceed=*/ true, /*shouldTimeout=*/ false);
  }
  
  private static void assertReadFails(DatabaseReference ref) throws InterruptedException {
    doRead(ref, /*shouldSucceed=*/ false, /*shouldTimeout=*/ false);
  }

  private static void doWrite(
      DatabaseReference ref, final boolean shouldSucceed, final boolean shouldTimeout)
      throws InterruptedException {
    final CountDownLatch lock = new CountDownLatch(1);
    final AtomicBoolean success = new AtomicBoolean(false);
    ApiFutures.addCallback(ref.setValueAsync("wrote something"), new ApiFutureCallback<Void>() {
      @Override
      public void onFailure(Throwable throwable) {
        success.compareAndSet(false, false);
        lock.countDown();
      }

      @Override
      public void onSuccess(Void result) {
        success.compareAndSet(false, true);
        lock.countDown();
      }
    });
    boolean finished = lock.await(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    if (shouldTimeout) {
      assertTrue("Write finished (expected to timeout).", !finished);
    } else if (shouldSucceed) {
      assertTrue("Write timed out (expected to succeed)", finished);
      assertTrue("Write failed (expected to succeed).", success.get());
    } else {
      assertTrue("Write timed out (expected to fail).", finished);
      assertTrue("Write successful (expected to fail).", !success.get());
    }
  }
  
  private static void doRead(
      DatabaseReference ref, final boolean shouldSucceed, final boolean shouldTimeout)
      throws InterruptedException {
    final CountDownLatch lock = new CountDownLatch(1);
    final AtomicBoolean success = new AtomicBoolean(false);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            success.compareAndSet(false, true);
            lock.countDown();
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            lock.countDown();
          }
        });

    boolean finished = lock.await(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    if (shouldTimeout) {
      assertTrue("Read finished (expected to timeout).", !finished);
    } else if (shouldSucceed) {
      assertTrue("Read timed out (expected to succeed).", finished);
      assertTrue("Read failed (expected to succeed).", success.get());
    } else {
      assertTrue("Read timed out (expected to fail).", finished);
      assertTrue("Read successful (expected to fail).", !success.get());
    }
  }
  
  private static void setDatabaseRules() throws IOException {
    // TODO: Use more than uid in rule Set rules so the only allowed operation is writing to 
    // /test-uid-only by user with uid 'test'.
    String rules =
        "{\n"
            + "  \"rules\": {\n"
            + "    \"test-uid-only\": {\n"
            + "      \".read\":  \"auth.uid == 'test'\",\n"
            + "      \".write\": \"auth.uid == 'test'\"\n"
            + "    },\n"
            + "    \"test-custom-field-only\": {\n"
            + "      \".read\": \"auth.custom == 'secret'\",\n"
            + "      \".write\": \"auth.custom == 'secret'\"\n"
            + "    },\n"
            + "    \"test-noauth-only\": {\n"
            + "      \".write\": \"auth == null\"\n"
            + "    }\n"
            + "  }\n"
            + "}";

    AppHttpClient client = new AppHttpClient();
    ResponseInfo info = client.put("/.settings/rules.json", rules);
    assertEquals(200, info.getStatus());
  }
}
