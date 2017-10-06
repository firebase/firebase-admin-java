package com.google.firebase.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.ServiceAccount;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;

public class FirestoreClientTest {

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testExplicitProjectId() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("explicit-project-id")
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());

    firestore = FirestoreClient.getFirestore();
    assertEquals("explicit-project-id", firestore.getOptions().getProjectId());
  }

  @Test
  public void testServiceAccountProjectId() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .build());
    Firestore firestore = FirestoreClient.getFirestore(app);
    assertEquals("mock-project-id", firestore.getOptions().getProjectId());

    firestore = FirestoreClient.getFirestore();
    assertEquals("mock-project-id", firestore.getOptions().getProjectId());
  }

  @Test
  public void testAppDelete() throws IOException {
    FirebaseApp app = FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(ServiceAccount.EDITOR.asStream()))
        .setProjectId("mock-project-id")
        .build());

    assertNotNull(FirestoreClient.getFirestore(app));
    app.delete();
    try {
      FirestoreClient.getFirestore(app);
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
