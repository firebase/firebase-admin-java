package com.google.firebase.internal;

import static org.junit.Assert.assertTrue;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

public class FirebaseAppStoreTest {

  private static final String FIREBASE_DB_URL = "https://mock-project.firebaseio.com";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      new FirebaseOptions.Builder()
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setCredential(TestUtils.getCertCredential(ServiceAccount.EDITOR.asStream()))
          .build();

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  @Test
  public void compatibleAppInitializedInNextRunOk() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
  }

  @Test
  public void incompatibleAppInitializedDoesntThrow() throws IOException {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp.initializeApp(options, name);
  }

  @Test
  public void incompatibleDefaultAppInitializedDoesntThrow() throws IOException {
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
            .build();
    FirebaseApp.initializeApp(options);
  }

  @Test
  public void persistenceDisabled() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseAppStore appStore = FirebaseAppStore.getInstance();
    assertTrue(!appStore.getAllPersistedAppNames().contains(name));
  }
}
