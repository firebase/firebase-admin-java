package com.google.firebase.internal;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.testing.FirebaseAppRule;
import com.google.firebase.testing.ServiceAccount;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FirebaseAppStoreTest {

  private static final String FIREBASE_DB_URL = "https://mock-project.firebaseio.com";

  private static final FirebaseOptions ALL_VALUES_OPTIONS =
      new FirebaseOptions.Builder()
          .setDatabaseUrl(FIREBASE_DB_URL)
          .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
          .build();

  @Rule
  public FirebaseAppRule mFirebaseAppRule = new FirebaseAppRule();

  // TODO(arondeak): reenable persistence. See b/28158809.
  //    @Test
  //    public void persistThenRestoreOneApp() {
  //        String name = "myApp";
  //        FirebaseApp.initializeApp(mTargetContext, ALL_VALUES_OPTIONS, name);
  //        FirebaseAppStore appStore = new SharedPrefsFirebaseAppStore(mTargetContext)
  //        assertThat(appStore.getAllPersistedAppNames().contains(name)).isTrue();
  //        FirebaseApp.clearInstancesForTest();
  //        FirebaseApp restoredApp = Iterables.getOnlyElement(FirebaseApp.getApps
  // (mTargetContext));
  //        assertThat(restoredApp.getOptions()).isEqualTo(ALL_VALUES_OPTIONS);
  //    }
  //
  //    @Test
  //    public void persistThenRemoveOneApp() {
  //        String name = "myApp";
  //        FirebaseApp.initializeApp(mTargetContext, ALL_VALUES_OPTIONS, name);
  //        FirebaseAppStore appStore = new SharedPrefsFirebaseAppStore(mTargetContext);
  //        assertThat(appStore.getAllPersistedAppNames().contains(name)).isTrue();
  //        appStore.removeApp(name);
  //        assertThat(appStore.getAllPersistedAppNames()).doesNotContain(name);
  //    }

  @Test
  public void compatibleAppInitializedInNextRunOk() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
  }


  @Test
  public void incompatibleAppInitializedDoesntThrow() {
    String name = "myApp";
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS, name);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredential(FirebaseCredentials.fromCertificate(ServiceAccount.EDITOR.asStream()))
        .build();
    FirebaseApp.initializeApp(options, name);
  }

  @Test
  public void incompatibleDefaultAppInitializedDoesntThrow() {
    FirebaseApp.initializeApp(ALL_VALUES_OPTIONS);
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseOptions options = new FirebaseOptions.Builder()
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
