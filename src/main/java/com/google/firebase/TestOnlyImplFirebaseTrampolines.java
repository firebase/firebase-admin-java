package com.google.firebase;

import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.tasks.Task;

/**
 * Provides trampolines into package-private APIs used by components of Firebase
 *
 * Intentionally scarily-named to dissuade people from actually trying to use the class and to make
 * it less likely to appear in code completion.
 *
 * This class will not be compiled into the shipping library and can only be used in tests.
 *
 * @hide
 */
public final class TestOnlyImplFirebaseTrampolines {

  private TestOnlyImplFirebaseTrampolines() {
  }

  /* FirebaseApp */
  public static void clearInstancesForTest() {
    FirebaseApp.clearInstancesForTest();
  }

  public static Task<GetTokenResult> getToken(FirebaseApp app, boolean forceRefresh) {
    return app.getToken(forceRefresh);
  }
}

