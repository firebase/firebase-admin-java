package com.google.firebase.database.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.testing.IntegrationTestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseDatabaseTestIT {
  
  @BeforeClass
  public static void setUpClass() {
    IntegrationTestUtils.initDefaultApp();
  }
  
  @AfterClass
  public static void tearDownClass() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetDefaultInstance() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(FirebaseApp.getInstance().getOptions().getDatabaseUrl(), 
        db.getReference().toString());
  }
  
  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = appWithDbUrl(IntegrationTestUtils.getDatabaseUrl(), "testGetInstanceForApp");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    assertEquals(app.getOptions().getDatabaseUrl(), db.getReference().toString());
  }

  @Test
  public void testNullDatabaseUrl() {
    FirebaseApp app = appWithDbUrl(null, "nullDbUrl");
    try {
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for getInstance() with null URL");
    } catch (DatabaseException expected) { // ignore
    }
  }

  @Test
  public void testMalformedDatabaseUrlInOptions() {
    FirebaseApp app = appWithDbUrl("not-a-url", "malformedDbUrlInOptions");
    try {
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for getInstance() with malformed URL");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testMalformedDatabaseUrlInGetInstance() {
    FirebaseApp app = appWithoutDbUrl("malformedDbUrlInGetInstance");
    try {
      FirebaseDatabase.getInstance(app, "not-a-url");
      fail("no error thrown for getInstance() with malformed URL");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testDatabaseUrlWithPathInOptions() {
    FirebaseApp app = appWithDbUrl(IntegrationTestUtils.getDatabaseUrl() 
        + "/paths/are/not/allowed", "dbUrlWithPathInOptions");
    try {      
      FirebaseDatabase.getInstance(app);
      fail("no error thrown for DB URL with path");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testDatabaseUrlWithPathInGetInstance() {
    FirebaseApp app = appWithoutDbUrl("dbUrlWithPathInGetInstance");
    try {      
      FirebaseDatabase.getInstance(app, IntegrationTestUtils.getDatabaseUrl() 
          + "/paths/are/not/allowed");
      fail("no error thrown for DB URL with path");
    } catch (DatabaseException expected) { // ignore
    }
  }
  
  @Test
  public void testGetReference() {
    FirebaseDatabase db = FirebaseDatabase.getInstance();
    assertEquals(IntegrationTestUtils.getDatabaseUrl() + "/foo", 
        db.getReference("foo").toString());
  }

  @Test
  public void testGetReferenceFromURLWithoutPath() {
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
  
  private static FirebaseApp appWithDbUrl(String dbUrl, String name) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setDatabaseUrl(dbUrl)
        .setCredential(FirebaseCredentials.fromCertificate(
            IntegrationTestUtils.getServiceAccountCertificate()))
        .build();
    return FirebaseApp.initializeApp(options, name);
  }
  
  private static FirebaseApp appWithoutDbUrl(String name) {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(
            IntegrationTestUtils.getServiceAccountCertificate()))
        .build();
    return FirebaseApp.initializeApp(options, name);
  }
}
