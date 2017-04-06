package com.google.firebase;

/** 
 * A listener which gets notified when {@link com.google.firebase.FirebaseApp} gets deleted. 
 */
// TODO(arondeak): consider making it public in a future release.
interface FirebaseAppLifecycleListener {

  /**
   * Gets called when {@link FirebaseApp#delete()} is called. {@link FirebaseApp} public methods
   * start throwing after delete is called, so name and options are passed in to be able to identify
   * the instance.
   */
  void onDeleted(String firebaseAppName, FirebaseOptions options);
}
