package com.google.firebase.internal;

import static com.google.firebase.internal.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Responsible for the persistence of FirebaseApps.
 */
// TODO(arondeak): reenable persistence. See b/28158809.
// TODO(depoll): Make this an independent implementation using Preferences
//   once the Shared Preferences version is stable and re-enabled.
public class SharedPrefsFirebaseAppStore extends FirebaseAppStore {

  // Increment this number if you make a backwards incompatible change to the storage format.
  // As currently implemented an increase of the data format version results in an ISE getting
  // thrown.
  // TODO(arondeak): Change this behavior when this value is changed.
  private static final int DATA_FORMAT_VERSION = 1;
  private static final String KEY_DATA_FORMAT_VERSION_VERSION = "version";
  private static final String KEY_FIREBASE_APP_NAMES = "firebase-app-names";
  private static final String KEY_PREFIX_API_KEY = "apiKey-";
  private static final String KEY_PREFIX_APP_ID = "appId-";
  private static final String KEY_PREFIX_DATABASE_URL = "dbUrl-";
  private static final String KEY_PREFIX_GA_TRACKING_ID = "gaTrackingId-";
  private static final String KEY_PREFIX_GCM_SENDER_ID = "gcmSenderId-";
  private static final String KEY_PREFIX_STORAGE_BUCKET = "storageBucket-";
  private static final String VALUE_SEPARATOR = ",";

  /**
   * Used to make multiple SharedPreferences reads/writes an atomic operation. Not necessary around
   * single reads.
   */
  private final Object mLock = new Object();

  private Preferences mPreferences;

  SharedPrefsFirebaseAppStore() {
  }

  /**
   * The returned set is mutable.
   */
  @Override
  public Set<String> getAllPersistedAppNames() {
    ensurePrefsInitialized();
    List<String> encodedAppNames = getEncodedAppNames();
    Set<String> persistedAppNames = new HashSet<>();
    for (String encodedAppName : encodedAppNames) {
      persistedAppNames.add(decodeValue(encodedAppName));
    }
    return persistedAppNames;
  }

  @Override
  public void persistApp(@NonNull FirebaseApp app) {
    synchronized (mLock) {
      Preferences prefs = ensurePrefsInitialized();
      String encodedAppName = encodeValue(app.getName());
      String encodedAppNamesValue = prefs.get(KEY_FIREBASE_APP_NAMES, "");
      List<String> encodedAppNames = asList(encodedAppNamesValue.split(VALUE_SEPARATOR));

      if (!ImplFirebaseTrampolines.isDefaultApp(app) && encodedAppNames.contains(encodedAppName)) {
        checkPersistedAppCompatible(app);
        return;
      }
      FirebaseOptions options = app.getOptions();
      prefs.put(KEY_FIREBASE_APP_NAMES, encodedAppNamesValue + VALUE_SEPARATOR + encodedAppName);
      // TODO(depoll): Make sure this has all of the options -- not just the DB URL.
      writeValue(prefs, KEY_PREFIX_DATABASE_URL + encodedAppName, options.getDatabaseUrl());
    }
  }

  @Override
  public void removeApp(@NonNull String name) {
    synchronized (mLock) {
      Preferences prefs = ensurePrefsInitialized();
      String encodedAppName = encodeValue(name);
      String encodedAppNamesValue = prefs.get(KEY_FIREBASE_APP_NAMES, "");
      List<String> encodedAppNames = asList(encodedAppNamesValue.split(VALUE_SEPARATOR));
      List<String> updatedEncodedAppNames = new ArrayList<>(encodedAppNames);
      updatedEncodedAppNames.remove(encodedAppName);
      prefs.put(KEY_FIREBASE_APP_NAMES, Joiner.on(VALUE_SEPARATOR).join(updatedEncodedAppNames));
      prefs.remove(KEY_PREFIX_API_KEY + encodedAppName);
      prefs.remove(KEY_PREFIX_APP_ID + encodedAppName);
      prefs.remove(KEY_PREFIX_DATABASE_URL + encodedAppName);
      prefs.remove(KEY_PREFIX_GA_TRACKING_ID + encodedAppName);
      prefs.remove(KEY_PREFIX_GCM_SENDER_ID + encodedAppName);
      prefs.remove(KEY_PREFIX_STORAGE_BUCKET + encodedAppName);
    }
  }

  /**
   * @return The restored {@link FirebaseOptions}, or null if it doesn't exist.
   */
  @Override
  public FirebaseOptions restoreAppOptions(@NonNull String name) {
    synchronized (mLock) {
      Preferences prefs = ensurePrefsInitialized();
      String encodedName = encodeValue(name);
      String applicationId = prefs.get(KEY_PREFIX_APP_ID + encodedName, null);
      if (applicationId == null) {
        return null;
      }
      return new FirebaseOptions.Builder()
          .setDatabaseUrl(readValue(prefs, KEY_PREFIX_DATABASE_URL + encodedName))
          .build();
      // TODO(depoll): Ensure all of the options are included, not just DB URL.
    }
  }

  private void checkPersistedAppCompatible(FirebaseApp app) {
    String name = app.getName();
    FirebaseOptions options = restoreAppOptions(name);
    // This check is probably too restrictive. However it is easier to move from a more
    // restrictive check to a more lenient one than doing the reverse.
    // TODO(arondeak): can we be less restrictive here?
    checkState(
        options.equals(app.getOptions()),
        "FirebaseApp "
            + app.getName()
            + " incompatible with persisted version! Persisted options "
            + options
            + " Newly initialized app options "
            + app.getOptions());
  }

  private Preferences ensurePrefsInitialized() {
    synchronized (mLock) {
      if (mPreferences == null) {
        mPreferences = Preferences.userNodeForPackage(FirebaseApp.class);
        int readDataVersion = mPreferences.getInt(KEY_DATA_FORMAT_VERSION_VERSION, -1);
        if (readDataVersion == -1) {
          resetStore();
        } else if (readDataVersion != DATA_FORMAT_VERSION) {
          // Data in Preferences is an older format.
          // TODO(arondeak): come up with something better before an SDK with an
          // incremented version is released.
          throw new IllegalStateException(
              String.format(
                  "Unexpected data format version. Was %d, but expected %d.",
                  readDataVersion, DATA_FORMAT_VERSION));
        }
      }
    }
    return mPreferences;
  }

  private List<String> getEncodedAppNames() {
    String encodedAppNamesValue = mPreferences.get(KEY_FIREBASE_APP_NAMES, "");
    List<String> encodedAppNames = new ArrayList<>();
    for (String encodedAppName : encodedAppNamesValue.split(VALUE_SEPARATOR)) {
      // Filter empty values. Split returns a non-empty array for an empty string.
      if (encodedAppName != null && !encodedAppName.equals("")) {
        encodedAppNames.add(encodedAppName);
      }
    }
    return encodedAppNames;
  }

  /**
   * Clears all data in {@link Preferences}
   */
  @Override
  protected void resetStore() {
    try {
      mPreferences.clear();
    } catch (BackingStoreException e) {
      throw new IllegalStateException("Could not clear Preferences", e);
    }
    mPreferences.putInt(KEY_DATA_FORMAT_VERSION_VERSION, DATA_FORMAT_VERSION);
  }

  private static String encodeValue(String value) {
    if (value == null) {
      value = "";
    }
    return Base64Utils.encodeUrlSafeNoPadding(value.getBytes(UTF_8));
  }

  /**
   * @throws IllegalArgumentException if value is not valid websafe, no padding base64.
   */
  private static String decodeValue(String encodedValue) {
    String decodedValue = new String(Base64Utils.decodeUrlSafeNoPadding(encodedValue), UTF_8);
    if (decodedValue == null || decodedValue.equals("")) {
      // FirebaseOptions values are null by default. Restoring values to empty string would
      // break equality check between original and restored instance.
      return null;
    }
    return decodedValue;
  }

  private static void writeValue(Preferences prefs, String key, String value) {
    if (value != null) {
      prefs.put(key, encodeValue(value));
    }
  }

  private static String readValue(Preferences prefs, String key) {
    String encodedValue = prefs.get(key, null);
    if (encodedValue != null) {
      return decodeValue(encodedValue);
    }
    return null;
  }
}
