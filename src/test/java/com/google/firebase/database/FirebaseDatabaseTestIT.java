package com.google.firebase.database;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.testing.IntegrationTestUtils;
import com.google.firebase.testing.ServiceAccount;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class FirebaseDatabaseTestIT {

  private static FirebaseApp emptyApp(String appId) {
    return appForDatabaseUrl(null, appId);
  }

  private static FirebaseApp appForDatabaseUrl(String url, String name) {
    return FirebaseApp.initializeApp(
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .setDatabaseUrl(url)
            .build(),
        name);
  }
  
  @BeforeClass
  public static void setUpClass() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(IntegrationTestUtils.getDatabaseUrl())
        .setCredential(FirebaseCredentials.fromCertificate(
            IntegrationTestUtils.getServiceAccountCertificate()))
        .build();
    FirebaseApp.initializeApp(options);
  }
  
  @AfterClass
  public static void tearDownClass() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @After
  public void tearDown() {
    TestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void testGetDefaultInstance() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(
        FirebaseApp.getInstance().getOptions().getDatabaseUrl(), db.getReference().toString());
  }

  @Test
  public void testGetInstanceForApp() {
    String dbUrl = IntegrationTestUtils.getDatabaseUrl();
    FirebaseApp app = appForDatabaseUrl(dbUrl, "getInstanceForApp");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    assertEquals(dbUrl, db.getReference().toString());
  }

  @Test
  public void testGetInstanceForAppWithInvalidUrls() {
    try {
      FirebaseApp app = appForDatabaseUrl(null, "getInstanceForAppWithInvalidUrls-0");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = appForDatabaseUrl("not-a-url", "getInstanceForAppWithInvalidUrls-1");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = emptyApp("getInstanceForAppWithInvalidUrls-2");
      FirebaseDatabase.getInstance(app, "not-a-url");
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app =
          appForDatabaseUrl(
              "http://x.fblocal.com:9000/paths/are/not/allowed",
              "getInstanceForAppWithInvalidUrls-3");
      FirebaseDatabase.getInstance(app);
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }

    try {
      FirebaseApp app = emptyApp("getInstanceForAppWithInvalidUrls-4");
      FirebaseDatabase.getInstance(app, "http://x.fblocal.com:9000/paths/are/not/allowed");
      fail("should throw");
    } catch (DatabaseException e) {
      // expected
    }
  }

  @Test
  public void testGetReference() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(IntegrationTestUtils.getDatabaseUrl() + "/foo", db.getReference("foo").toString());
  }

  @Test
  public void testGetReferenceFromURLWithEmptyPath() {
    String dbUrl = IntegrationTestUtils.getDatabaseUrl();
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    DatabaseReference ref = db.getReferenceFromUrl(dbUrl);
    assertEquals(dbUrl, ref.toString());
  }

  @Test
  public void testGetReferenceFromURLWithPath() {
    String dbUrl = IntegrationTestUtils.getDatabaseUrl();
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    DatabaseReference ref = db.getReferenceFromUrl(dbUrl + "/foo/bar");
    assertEquals(dbUrl + "/foo/bar", ref.toString());
  }

  @Test(expected = DatabaseException.class)
  public void testGetReferenceThrowsWithBadUrl() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    db.getReferenceFromUrl("http://tests2.fblocal.com:9000");
  }
}
