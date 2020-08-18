package com.google.firebase.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

public class FirestoreClientTest {

  private static final FirestoreOptions FIRESTORE_OPTIONS = FirestoreOptions.newBuilder()
      // Setting credentials is not required (they get overridden by Admin SDK), but without
      // this Firestore logs an ugly warning during tests.
      .setCredentials(new MockGoogleCredentials("test-token"))
      .build();

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testExplicitProjectId() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());

    firestore = FirestoreClient.getFirestore();
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
  }

  @Test
  public void testServiceAccountProjectId() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("mock-project-id", firestore.getOptions().getProjectId());

    firestore = FirestoreClient.getFirestore();
    assertEquals("mock-project-id", firestore.getOptions().getProjectId());
  }

  @Test
  public void testFirestoreOptions() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
    assertTrue(firestore.getOptions().areTimestampsInSnapshotsEnabled());

    firestore = FirestoreClient.getFirestore();
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
    assertTrue(firestore.getOptions().areTimestampsInSnapshotsEnabled());
  }

  @Test
  public void testFirestoreOptionsOverride() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .setFirestoreOptions(FirestoreOptions.newBuilder()
            .setProjectId("other-project-id")
            .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
            .build())
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
    assertTrue(firestore.getOptions().areTimestampsInSnapshotsEnabled());
    assertSame(ImplFirebaseTrampolines.getCredentials(app),
        firestore.getOptions().getCredentialsProvider().getCredentials());

    firestore = FirestoreClient.getFirestore();
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
    assertTrue(firestore.getOptions().areTimestampsInSnapshotsEnabled());
    assertSame(ImplFirebaseTrampolines.getCredentials(app),
        firestore.getOptions().getCredentialsProvider().getCredentials());
  }

  @Test
  public void testAppDelete() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("mock-project-id")
        .setFirestoreOptions(FIRESTORE_OPTIONS)
        .build());

    Firestore firestore = FirestoreClient.getFirestore(app);
    assertNotNull(firestore);
    DocumentReference document = firestore.collection("collection").document("doc");
    app.delete();
    try {
      FirestoreClient.getFirestore(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      document.get();
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }

    try {
      FirestoreClient.getFirestore();
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }
}
