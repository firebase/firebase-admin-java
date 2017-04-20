package com.google.firebase.database;

/**
 * This error is thrown when the Firebase Database library is unable to operate on the input it has
 * been given.
 */
public class DatabaseException extends RuntimeException {

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param message A human readable description of the error
   */
  public DatabaseException(String message) {
    super(message);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param message A human readable description of the error
   * @param cause The underlying cause for this error
   */
  public DatabaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
