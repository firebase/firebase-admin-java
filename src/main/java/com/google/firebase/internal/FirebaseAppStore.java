package com.google.firebase.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** No-op base class of FirebaseAppStore. */
public class FirebaseAppStore {

  private static final AtomicReference<FirebaseAppStore> sInstance = new AtomicReference<>();

  FirebaseAppStore() {}

  @Nullable
  public static FirebaseAppStore getInstance() {
    return sInstance.get();
  }

  // TODO(arondeak): reenable persistence. See b/28158809.
  public static FirebaseAppStore initialize() {
    sInstance.compareAndSet(null /* expected */, new FirebaseAppStore());
    return sInstance.get();
  }

  public static void setInstanceForTest(FirebaseAppStore firebaseAppStore) {
    sInstance.set(firebaseAppStore);
  }

  @VisibleForTesting
  public static void clearInstanceForTest() {
    FirebaseAppStore instance = sInstance.get();
    if (instance != null) {
      instance.resetStore();
    }
    sInstance.set(null);
  }

  /** The returned set is mutable. */
  public Set<String> getAllPersistedAppNames() {
    return Collections.emptySet();
  }

  public void persistApp(@NonNull FirebaseApp app) {}

  public void removeApp(@NonNull String name) {}

  /** 
   * @return The restored {@link FirebaseOptions}, or null if it doesn't exist.
   */
  public FirebaseOptions restoreAppOptions(@NonNull String name) {
    return null;
  }

  protected void resetStore() {}
}
