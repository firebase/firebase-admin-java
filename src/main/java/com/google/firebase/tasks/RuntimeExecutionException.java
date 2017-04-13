package com.google.firebase.tasks;

/**
 * Runtime version of {@link java.util.concurrent.ExecutionException}.
 *
 * @see Task#getResult(Class)
 */
public class RuntimeExecutionException extends RuntimeException {

  public RuntimeExecutionException(Throwable cause) {
    super(cause);
  }
}
