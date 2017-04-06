package com.google.firebase.integration;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.OnCompleteListener;
import com.google.firebase.tasks.Task;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DatabaseServerAuthTestIT {

  // You can swap in the production Firebase URL to test against production.
  private static final String SERVER_SDK_DB_URL = "http://server-sdk-test.fblocal.com:9000";
  //private static final String SERVER_SDK_DB_URL = "https://server-sdk-test-3ff90.firebaseio
  // .com";

  private static final String FBLOCAL_ADMIN_KEY = "1234";
  private static FirebaseApp masterApp;

  @BeforeClass
  public static void setup() {
    createFirebaseApp();
    if (SERVER_SDK_DB_URL.contains("fblocal")) {
      createTestDatabase();
    }
    setDatabaseRules();
  }

  private static void createFirebaseApp() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(SERVER_SDK_DB_URL)
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .build();
    masterApp = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestIT");
  }

  private static void createTestDatabase() {
    // Make sure local server-sdk-test namespace exists mapped to the correct project_id and
    // number.
    String creationParams = "{\n"
        + "  \"project_id\": \"" + TestUtils.PROJECT_ID + "\",\n"
        + "  \"project_number\": \"" + TestUtils.PROJECT_NUMBER + "\"\n"
        + "}";

    doFbLocalAdminRestPut("/.nsadmin/.json", creationParams);
  }

  private static void setDatabaseRules() {
    // TODO(depoll): Use more than uid in rule
    // Set rules so the only allowed operation is writing to /test-uid-only by user with uid
    // 'test'.
    String rules =
        "{\n"
            + "  \"rules\": {\n"
            + "    \"test-uid-only\": {\n"
            + "      \".write\": \"auth.uid == 'test'\"\n"
            + "    },\n"
            + "    \"test-custom-field-only\": {\n"
            + "      \".write\": \"auth.custom == 'secret'\"\n"
            + "    },\n"
            + "    \"test-noauth-only\": {\n"
            + "      \".write\": \"auth == null\"\n"
            + "    }\n"
            + "  }\n"
            + "}";

    doServerAccountRestPut("/.settings/rules.json", rules);
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

  private static void doWrite(DatabaseReference ref,
                              final boolean shouldSucceed,
                              final boolean shouldTimeout) throws InterruptedException {
    final CountDownLatch lock = new CountDownLatch(1);
    ref.setValue("wrote something")
        .addOnCompleteListener(
            new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                assertEquals(shouldSucceed, task.isSuccessful());
                lock.countDown();
              }
            });
    boolean finished = lock.await(TestUtils.ASYNC_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    if (shouldTimeout) {
      assertTrue("Write finished.", !finished);
    } else {
      assertTrue("Write timed out.", finished);
    }
  }

  private static void assertReadSucceeds(DatabaseReference ref) throws InterruptedException {
    doRead(ref, /*shouldSucceed=*/ true, /*shouldTimeout=*/ false);
  }

  private static void assertReadFails(DatabaseReference ref) throws InterruptedException {
    doRead(ref, /*shouldSucceed=*/ false, /*shouldTimeout=*/ false);
  }

  private static void assertReadTimeout(DatabaseReference ref) throws InterruptedException {
    doRead(ref, /*shouldSucceed=*/ false, /*shouldTimeout=*/ true);
  }

  private static void doRead(DatabaseReference ref,
                             final boolean shouldSucceed,
                             final boolean shouldTimeout) throws InterruptedException {
    final CountDownLatch lock = new CountDownLatch(1);
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        assertTrue("Read succeeded.", shouldSucceed);
        lock.countDown();
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        assertTrue("Read cancelled.", !shouldSucceed);
        lock.countDown();
      }
    });

    boolean finished = lock.await(TestUtils.ASYNC_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
    if (shouldTimeout) {
      assertTrue("Read finished.", !finished);
    } else {
      assertTrue("Read timed out.", finished);
    }
  }

  private static void doFbLocalAdminRestPut(String endpoint, String data) {
    doRestPut(SERVER_SDK_DB_URL + endpoint + "?key=" + FBLOCAL_ADMIN_KEY, data);
  }

  private static void doServerAccountRestPut(final String endpoint, final String data) {
    // TODO(mikelehen): We should consider exposing getToken (or similar) publicly for the
    // purpose
    // of servers doing authenticated REST requests like this.
    TestOnlyImplFirebaseTrampolines.getToken(masterApp, /*forceRefresh=*/ false)
        .addOnCompleteListener(
            new OnCompleteListener<GetTokenResult>() {
              @Override
              public void onComplete(@NonNull Task<GetTokenResult> task) {
                assertNull("getToken failed.", task.getException());
                String token = task.getResult().getToken();
                doRestPut(SERVER_SDK_DB_URL + endpoint + "?access_token=" + token, data);
              }
            });
  }

  private static void doRestPut(String endpoint, String data) {
    HttpResponse response;
    try {
      HttpPut put = new HttpPut(endpoint);
      HttpEntity entity = new StringEntity(data, "UTF-8");
      put.setEntity(entity);

      HttpClient httpClient = new DefaultHttpClient();
      response = httpClient.execute(put);
    } catch (Exception e) {
      throw new RuntimeException("doRestPut failed", e);
    }
    assertTrue("Rest put for " + endpoint + " failed: " + response.toString(),
        response.getStatusLine().getStatusCode() == 200);
  }

  @Test
  public void testServiceAccountWithNoRole() throws InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertWriteSucceeds(db.getReference());

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(SERVER_SDK_DB_URL)
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.NONE.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestNoRole");
    db = FirebaseDatabase.getInstance(app);
    // TODO(klimt): Ideally, we would find a way to verify the correct log output.
    assertReadTimeout(db.getReference());
    assertWriteTimeout(db.getReference());
  }

  @Test
  public void testServiceAccountWithViewerRole() throws InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertWriteSucceeds(db.getReference());

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(SERVER_SDK_DB_URL)
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.VIEWER.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestViewerRole");
    db = FirebaseDatabase.getInstance(app);
    assertReadSucceeds(db.getReference());
    assertWriteFails(db.getReference());
  }

  @Test
  public void testServiceAccountWithEditorRole() throws InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertWriteSucceeds(db.getReference());

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(SERVER_SDK_DB_URL)
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestEditorRole");
    db = FirebaseDatabase.getInstance(app);
    assertReadSucceeds(db.getReference());
    assertWriteSucceeds(db.getReference());
  }

  @Test
  public void testServiceAccountWithOwnerRole() throws InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertWriteSucceeds(db.getReference());

    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(SERVER_SDK_DB_URL)
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.OWNER.asStream()))
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "DatabaseServerAuthTestOwnerRole");
    db = FirebaseDatabase.getInstance(app);
    assertReadSucceeds(db.getReference());
    assertWriteSucceeds(db.getReference());
  }

  @Test
  public void testServiceAccountDatabaseAuth() throws IOException, InterruptedException {
    FirebaseDatabase db = FirebaseDatabase.getInstance(masterApp);
    assertWriteSucceeds(db.getReference());
  }

  @Test
  public void testServiceAccountDatabaseAuthVariables() throws IOException, InterruptedException {
    Map<String, Object> authVariableOverrides = new HashMap<>();
    authVariableOverrides.put("uid", "test");
    authVariableOverrides.put("custom", "secret");
    FirebaseOptions options = new FirebaseOptions.Builder(masterApp.getOptions())
        .setDatabaseAuthVariableOverride(authVariableOverrides)
        .build();
    FirebaseApp testUidApp = FirebaseApp.initializeApp(options, "testGetAppWithUid");

    FirebaseDatabase masterDb = FirebaseDatabase.getInstance(masterApp);
    FirebaseDatabase testAuthOverridesDb = FirebaseDatabase.getInstance(testUidApp);

    assertWriteSucceeds(masterDb.getReference());

    // "test" UID can only write to /test-uid-only/ and /test-custom-field-only/ locations.
    assertWriteFails(testAuthOverridesDb.getReference());
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-uid-only"));
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-custom-field-only"));
  }

  @Test
  public void testServiceAccountDatabaseWithNoAuth() throws IOException, InterruptedException {
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
    assertWriteSucceeds(testAuthOverridesDb.getReference("test-noauth-only"));
  }
}
