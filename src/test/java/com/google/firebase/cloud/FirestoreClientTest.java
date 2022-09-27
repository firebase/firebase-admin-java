package com.google.firebase.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public class FirestoreClientTest {

  private static final FirestoreOptions FIRESTORE_OPTIONS = FirestoreOptions.newBuilder()
      // Setting credentials is not required (they get overridden by Admin SDK), but without
      // this Firestore logs an ugly warning during tests.
      .setCredentials(new MockGoogleCredentials("test-token"))
      .setDatabaseId("differedDefaultDatabaseId")
      .build();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testExplicitProjectId() throws IOException {
    final String databaseId = "databaseIdInTestExplicitProjectId";
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore1 = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore1.getOptions().getProjectId());
    assertEquals(FIRESTORE_OPTIONS.getDatabaseId(), firestore1.getOptions().getDatabaseId());

    assertSame(firestore1, FirestoreClient.getFirestore());

    Firestore firestore2 = FirestoreClient.getFirestore(app, databaseId);
    assertEquals("explicit-project-id", firestore2.getOptions().getProjectId());
    assertEquals(databaseId, firestore2.getOptions().getDatabaseId());

    assertSame(firestore2, FirestoreClient.getFirestore(databaseId));

    assertNotSame(firestore1, firestore2);
  }

  @Test
  public void testServiceAccountProjectId() throws IOException {
    final String databaseId = "databaseIdInTestServiceAccountProjectId";
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore1 = FirestoreClient.getFirestore(app);
    assertEquals("mock-project-id", firestore1.getOptions().getProjectId());
    assertEquals(FIRESTORE_OPTIONS.getDatabaseId(), firestore1.getOptions().getDatabaseId());

    assertSame(firestore1, FirestoreClient.getFirestore());

    Firestore firestore2 = FirestoreClient.getFirestore(app, databaseId);
    assertEquals("mock-project-id", firestore2.getOptions().getProjectId());
    assertEquals(databaseId, firestore2.getOptions().getDatabaseId());

    assertSame(firestore2, FirestoreClient.getFirestore(databaseId));

    assertNotSame(firestore1, firestore2);
  }

  @Test
  public void testFirestoreOptions() throws IOException {
    final String databaseId = "databaseIdInTestFirestoreOptions";
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore1 = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore1.getOptions().getProjectId());
    assertEquals(FIRESTORE_OPTIONS.getDatabaseId(), firestore1.getOptions().getDatabaseId());

    assertSame(firestore1, FirestoreClient.getFirestore());

    Firestore firestore2 = FirestoreClient.getFirestore(app, databaseId);
    assertEquals("explicit-project-id", firestore2.getOptions().getProjectId());
    assertEquals(databaseId, firestore2.getOptions().getDatabaseId());

    assertSame(firestore2, FirestoreClient.getFirestore(databaseId));

    assertNotSame(firestore1, firestore2);
  }

  @Test
  public void testFirestoreOptionsOverride() throws IOException {
    final String databaseId = "databaseIdInTestFirestoreOptions";
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FirestoreOptions.newBuilder()
            .setProjectId("other-project-id")
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .build())
        .build());
    Firestore firestore1 = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore1.getOptions().getProjectId());
    assertSame(ImplFirebaseTrampolines.getCredentials(app),
        firestore1.getOptions().getCredentialsProvider().getCredentials());
    assertEquals("(default)", firestore1.getOptions().getDatabaseId());

    assertSame(firestore1, FirestoreClient.getFirestore());

    Firestore firestore2 = FirestoreClient.getFirestore(app, databaseId);
    assertEquals("explicit-project-id", firestore2.getOptions().getProjectId());
    assertSame(ImplFirebaseTrampolines.getCredentials(app),
        firestore2.getOptions().getCredentialsProvider().getCredentials());
    assertEquals(databaseId, firestore2.getOptions().getDatabaseId());

    assertSame(firestore2, FirestoreClient.getFirestore(databaseId));

    assertNotSame(firestore1, firestore2);
  }

  @Test
  public void testAppDelete() throws IOException {
    final String databaseId = "databaseIdInTestAppDelete";
    final FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("mock-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());

    Firestore firestore1 = FirestoreClient.getFirestore(app);
    assertNotNull(firestore1);
    assertSame(firestore1, FirestoreClient.getFirestore());

    Firestore firestore2 = FirestoreClient.getFirestore(app, databaseId);
    assertNotNull(firestore2);
    assertSame(firestore2, FirestoreClient.getFirestore(databaseId));

    assertNotSame(firestore1, firestore2);

    final DocumentReference document = firestore1.collection("collection").document("doc");
    app.delete();

    assertThrows(IllegalStateException.class, new ThrowingRunnable() {
      public void run() {
        FirestoreClient.getFirestore(app);
      }
    });
    assertThrows(IllegalStateException.class, new ThrowingRunnable() {
      public void run() throws Throwable {
        document.get();
      }
    });
    assertThrows(IllegalStateException.class, new ThrowingRunnable() {
      public void run() throws Throwable {
        FirestoreClient.getFirestore();
      }
    });
    assertThrows(IllegalStateException.class, new ThrowingRunnable() {
      public void run() throws Throwable {
        FirestoreClient.getFirestore(app, databaseId);
      }
    });
    assertThrows(IllegalStateException.class, new ThrowingRunnable() {
      public void run() throws Throwable {
        FirestoreClient.getFirestore(databaseId);
      }
    });
  }
}
