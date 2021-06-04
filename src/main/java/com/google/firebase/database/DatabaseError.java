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

package com.google.firebase.database;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Instances of DatabaseError are passed to callbacks when an operation failed. They contain a
 * description of the specific error that occurred.
 */
public class DatabaseError {

  private static final Map<DatabaseErrorCode, String> errorReasons =
      ImmutableMap.<DatabaseErrorCode, String>builder()
        .put(DatabaseErrorCode.DATA_STALE,
            "The transaction needs to be run again with current data")
        .put(DatabaseErrorCode.OPERATION_FAILED,
            "The server indicated that this operation failed")
        .put(DatabaseErrorCode.PERMISSION_DENIED,
            "This client does not have permission to perform this operation")
        .put(DatabaseErrorCode.DISCONNECTED,
            "The operation had to be aborted due to a network disconnect")
        .put(DatabaseErrorCode.EXPIRED_TOKEN, "The supplied auth token has expired")
        .put(DatabaseErrorCode.INVALID_TOKEN, "The supplied auth token was invalid")
        .put(DatabaseErrorCode.MAX_RETRIES, "The transaction had too many retries")
        .put(DatabaseErrorCode.OVERRIDDEN_BY_SET,
            "The transaction was overridden by a subsequent set")
        .put(DatabaseErrorCode.UNAVAILABLE, "The service is unavailable")
        .put(DatabaseErrorCode.USER_CODE_EXCEPTION,
            "User code called from the Firebase Database runloop threw an exception:\n")
        // client codes
        .put(DatabaseErrorCode.NETWORK_ERROR,
            "The operation could not be performed due to a network error")
        .put(DatabaseErrorCode.WRITE_CANCELED, "The write was canceled by the user.")
        .put(DatabaseErrorCode.UNKNOWN_ERROR, "An unknown error occurred")
        .build();

  private static final Map<DatabaseErrorCode, ErrorCode> platformCodes =
      ImmutableMap.<DatabaseErrorCode, ErrorCode>builder()
        .put(DatabaseErrorCode.DATA_STALE, ErrorCode.FAILED_PRECONDITION)
        .put(DatabaseErrorCode.OPERATION_FAILED, ErrorCode.INTERNAL)
        .put(DatabaseErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED)
        .put(DatabaseErrorCode.DISCONNECTED, ErrorCode.UNKNOWN)
        .put(DatabaseErrorCode.EXPIRED_TOKEN, ErrorCode.PERMISSION_DENIED)
        .put(DatabaseErrorCode.INVALID_TOKEN, ErrorCode.PERMISSION_DENIED)
        .put(DatabaseErrorCode.MAX_RETRIES, ErrorCode.DEADLINE_EXCEEDED)
        .put(DatabaseErrorCode.OVERRIDDEN_BY_SET, ErrorCode.FAILED_PRECONDITION)
        .put(DatabaseErrorCode.UNAVAILABLE, ErrorCode.UNAVAILABLE)
        .put(DatabaseErrorCode.USER_CODE_EXCEPTION, ErrorCode.UNKNOWN)
        // client codes
        .put(DatabaseErrorCode.NETWORK_ERROR, ErrorCode.UNKNOWN)
        .put(DatabaseErrorCode.WRITE_CANCELED, ErrorCode.CANCELLED)
        .put(DatabaseErrorCode.UNKNOWN_ERROR, ErrorCode.UNKNOWN)
        .build();

  private static final Map<String, DatabaseErrorCode> errorCodes = new HashMap<>();

  static {

    // Firebase Database error codes
    errorCodes.put("datastale", DatabaseErrorCode.DATA_STALE);
    errorCodes.put("failure", DatabaseErrorCode.OPERATION_FAILED);
    errorCodes.put("permission_denied", DatabaseErrorCode.PERMISSION_DENIED);
    errorCodes.put("disconnected", DatabaseErrorCode.DISCONNECTED);
    errorCodes.put("expired_token", DatabaseErrorCode.EXPIRED_TOKEN);
    errorCodes.put("invalid_token", DatabaseErrorCode.INVALID_TOKEN);
    errorCodes.put("maxretries", DatabaseErrorCode.MAX_RETRIES);
    errorCodes.put("overriddenbyset", DatabaseErrorCode.OVERRIDDEN_BY_SET);
    errorCodes.put("unavailable", DatabaseErrorCode.UNAVAILABLE);

    // client codes
    errorCodes.put("network_error", DatabaseErrorCode.NETWORK_ERROR);
    errorCodes.put("write_canceled", DatabaseErrorCode.WRITE_CANCELED);
  }

  private final DatabaseErrorCode code;
  private final String message;
  private final String details;

  private DatabaseError(DatabaseErrorCode code, String message) {
    this(code, message, null);
  }

  private DatabaseError(DatabaseErrorCode code, String message, String details) {
    this.code = code;
    this.message = message;
    this.details = (details == null) ? "" : details;
  }

  /**
   * <strong>For internal use.</strong>
   *
   * @hide
   * @param status The status string
   * @return An error corresponding the to the status
   */
  public static DatabaseError fromStatus(String status) {
    return fromStatus(status, null);
  }

  /**
   * <strong>For internal use.</strong>
   *
   * @hide
   * @param status The status string
   * @param reason The reason for the error
   * @return An error corresponding the to the status
   */
  public static DatabaseError fromStatus(String status, String reason) {
    return fromStatus(status, reason, null);
  }

  /**
   * <strong>For internal use</strong>
   *
   * @hide
   * @param status The status string
   * @param reason The reason for the error
   * @param details Additional details or null
   * @return An error corresponding the to the status
   */
  public static DatabaseError fromStatus(String status, String reason, String details) {
    DatabaseErrorCode code = errorCodes.get(status.toLowerCase());
    if (code == null) {
      code = DatabaseErrorCode.UNKNOWN_ERROR;
    }

    String message = reason == null ? errorReasons.get(code) : reason;
    return new DatabaseError(code, message, details);
  }

  /**
   * <strong>For internal use.</strong>
   *
   * @hide
   * @param code The error code
   * @return An error corresponding the to the code
   */
  public static DatabaseError fromCode(DatabaseErrorCode code) {
    if (!errorReasons.containsKey(code)) {
      throw new IllegalArgumentException("Invalid Firebase Database error code: " + code);
    }
    String message = errorReasons.get(code);
    return new DatabaseError(code, message, null);
  }

  public static DatabaseError fromException(Throwable e) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    e.printStackTrace(printWriter);
    String reason = errorReasons.get(DatabaseErrorCode.USER_CODE_EXCEPTION)
        + stringWriter.toString();
    return new DatabaseError(DatabaseErrorCode.USER_CODE_EXCEPTION, reason);
  }

  /** 
   * @return One of the defined status codes, depending on the error.
   */
  public DatabaseErrorCode getCode() {
    return code;
  }

  /** 
   * @return A human-readable description of the error.
   */
  public String getMessage() {
    return message;
  }

  /** 
   * @return Human-readable details on the error and additional information, e.g. links to docs;
   */
  public String getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "DatabaseError: " + message;
  }

  /**
   * Can be used if a third party needs an Exception from Firebase Database for integration
   * purposes.
   *
   * @return An exception wrapping this error, with an appropriate message and no stack trace.
   */
  public DatabaseException toException() {
    return new DatabaseException(platformCodes.get(code), "Firebase Database error: " + message);
  }
}
