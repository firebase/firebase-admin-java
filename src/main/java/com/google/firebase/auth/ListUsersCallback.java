package com.google.firebase.auth;

/**
 * An async callback for iterating over user accounts.
 */
public interface ListUsersCallback {

  /**
   * This event is called for each user account in the Firebase project. The return value can be
   * used to interrupt the iteration.
   *
   * @param userRecord A non-null {@link ExportedUserRecord} instance.
   * @return True if the iteration should continue, and false to interrupt. If false is returned,
   *     the {@link #onComplete()} event will fire immediately, and iteration will come to a halt.
   */
  boolean onResult(ExportedUserRecord userRecord);

  /**
   * This event is called whenever the iteration comes to a graceful end without errors. This can be
   * due to iterating over all available user accounts, or due to an interrupt from the
   * {@link #onResult(ExportedUserRecord)} method.
   */
  void onComplete();

  /**
   * This event is called in the event of an error during iteration. The iteration will not
   * proceed any further, and the {@link #onComplete()} will not fire.
   *
   * @param e A non-null Exception.
   */
  void onError(Exception e);

}
