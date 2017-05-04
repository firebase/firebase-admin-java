/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  // TODO: reenable persistence. See b/28158809.
  public static FirebaseAppStore initialize() {
    sInstance.compareAndSet(null /* expected */, new FirebaseAppStore());
    return sInstance.get();
  }

  /**
   * @hide
   */
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
