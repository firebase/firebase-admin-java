package com.google.firebase.database;

import com.google.firebase.database.snapshot.Node;

/**
 * The Transaction class encapsulates the functionality needed to perform a transaction on the data
 * at a location. <br>
 * <br>
 * To run a transaction, provide a {@link Handler} to {@link
 * DatabaseReference#runTransaction(com.google.firebase.database.Transaction.Handler)}. That handler
 * will be passed the current data at the location, and must return a {@link Result}. A {@link
 * Result} can be created using either {@link Transaction#success(MutableData)} or {@link
 * com.google.firebase.database.Transaction#abort()}.
 */
public class Transaction {

  /**
   * @return A {@link Result} that aborts the transaction
   */
  public static Result abort() {
    return new Result(false, null);
  }

  /**
   * @param resultData The desired data at the location
   * @return A {@link Result} indicating the new data to be stored at the location
   */
  public static Result success(MutableData resultData) {
    return new Result(true, resultData.getNode());
  }

  /**
   * An object implementing this interface is used to run a transaction, and will be notified of
   * the results of the transaction.
   */
  public interface Handler {

    /**
     * This method will be called, <em>possibly multiple times</em>, with the current data at
     * this location. It is responsible for inspecting that data and returning a {@link Result}
     * specifying either the desired new data at the location or that the transaction should be
     * aborted. <br> <br> Since this method may be called repeatedly for the same transaction,
     * be extremely careful of any side effects that may be triggered by this method. In
     * addition, this method is called from within the Firebase Database library's run loop, so
     * care is also required when accessing data that may be in use by other threads in your
     * application. <br> <br> Best practices for this method are to rely only on the data that
     * is passed in.
     *
     * @param currentData The current data at the location. Update this to the desired data
     *     at the location
     * @return Either the new data, or an indication to abort the transaction
     */
    Result doTransaction(MutableData currentData);

    /**
     * This method will be called once with the results of the transaction.
     *
     * @param error null if no errors occurred, otherwise it contains a description of the error
     * @param committed True if the transaction successfully completed, false if it was aborted or
     *     an error occurred
     * @param currentData The current data at the location
     */
    void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData);
  }

  /**
   * Instances of this class represent the desired outcome of a single run of a {@link Handler}'s
   * doTransaction method. The options are:
   *
   * <ul>
   * <li>Set the data to the new value (success)
   * <li>abort the transaction
   * </ul>
   *
   * Instances are created using {@link Transaction#success(MutableData)} or {@link
   * com.google.firebase.database.Transaction#abort()}.
   */
  public static class Result {

    private boolean success;
    private Node data;

    private Result(boolean success, Node data) {
      this.success = success;
      this.data = data;
    }

    /**
     * @return Whether or not this result is a success
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * <strong>For internal use</strong>
     *
     * @return The data
     */
    public Node getNode() {
      return data;
    }
  }
}
